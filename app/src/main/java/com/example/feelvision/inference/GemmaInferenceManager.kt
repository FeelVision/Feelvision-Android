package com.feelvision.inference

import android.content.Context
import android.graphics.Bitmap
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.google.ai.edge.litertlm.*
import kotlin.coroutines.cancellation.CancellationException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaInferenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugLogBus: DebugLogBus
) {
    companion object {
        private const val MODEL_NAME    = "gemma-4-E2B-it.litertlm"
        // FIX 1: Increased MAX_TOKENS to 1024. Images consume a lot of tokens (often 256-1024).
        // Passing an image + text with MAX_TOKENS=512 causes a native overflow crash.
        private const val MAX_TOKENS    = 1024
        private const val TOP_K         = 40
        private const val TOP_P         = 0.95f
        private const val TEMPERATURE   = 0.7f
        private const val IMG_MAX       = 336
        private const val JPEG_QUALITY  = 85
        private const val TIMEOUT_MS    = 60_000L
    }

    // @Volatile ensures writes are immediately visible across all threads
    @Volatile private var engine: Engine?             = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var isInitialized               = false

    // initMutex      — prevents two coroutines initializing at the same time
    // inferenceMutex — ensures only one inference runs at a time
    // CRITICAL RULE  — always withContext { withLock { } }, never withLock { withContext { } }
    private val initMutex      = Mutex()
    private val inferenceMutex = Mutex()

    // ── Initialization ────────────────────────────────────────────────

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        // Fast path — skip the lock if already ready
        if (isInitialized && engine != null && conversation != null) {
            debugLogBus.log(DebugLogType.INFO, "GEMMA", "Already initialized, skipping")
            return@withContext Result.success(Unit)
        }

        initMutex.withLock {
            // Double-check inside lock — another coroutine may have just finished
            if (isInitialized && engine != null && conversation != null) {
                return@withContext Result.success(Unit)
            }

            debugLogBus.log(DebugLogType.INFO, "GEMMA", "Searching for model...")

            val modelFile = findModelFile()
            if (modelFile == null) {
                val paths = getSearchPaths().joinToString("\n") { " → $it" }
                val err   = "Model not found. Searched:\n$paths"
                debugLogBus.log(DebugLogType.ERROR, "GEMMA", err)
                return@withContext Result.failure(Exception(err))
            }

            debugLogBus.log(
                DebugLogType.INFO, "GEMMA",
                "Found: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)"
            )

            // Try GPU first, fall back to CPU automatically
            val result =
                tryInitWithBackend(modelFile.absolutePath, useGpu = true)
                    ?: tryInitWithBackend(modelFile.absolutePath, useGpu = false)

            result ?: return@withContext Result.failure(
                Exception("Both GPU and CPU initialization failed")
            )
        }
    }

    private fun tryInitWithBackend(path: String, useGpu: Boolean): Result<Unit>? {
        val label = if (useGpu) "GPU" else "CPU"
        debugLogBus.log(DebugLogType.INFO, "GEMMA", "Trying $label backend...")
        return try {
            val cfg = EngineConfig(
                modelPath     = path,
                backend       = if (useGpu) Backend.GPU() else Backend.CPU(),
                visionBackend = if (useGpu) Backend.GPU() else Backend.CPU(),
                audioBackend  = Backend.CPU(),
                maxNumTokens  = MAX_TOKENS
            )
            val eng = Engine(cfg)
            eng.initialize()  // GPU throws here if unsupported — CPU does not

            engine        = eng
            conversation  = eng.createConversation(conversationConfig())
            isInitialized = true

            debugLogBus.log(DebugLogType.OK, "GEMMA", "Ready ($label)")
            Result.success(Unit)
        } catch (e: Exception) {
            return if (useGpu) {
                debugLogBus.log(
                    DebugLogType.WARN, "GEMMA",
                    "GPU failed: ${e.message} — will retry with CPU"
                )
                null  // signal caller to retry with CPU
            } else {
                debugLogBus.log(
                    DebugLogType.ERROR, "GEMMA",
                    "CPU also failed: ${e.message}"
                )
                Result.failure(e)
            }
        }
    }

    private fun conversationConfig() = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK        = TOP_K,
            topP        = TOP_P.toDouble(),
            temperature = TEMPERATURE.toDouble()
        )
    )

    // ── Sync generate ─────────────────────────────────────────────────

    suspend fun generate(
        prompt: String,
        images: List<Bitmap>     = emptyList(),
        baseSystemPrompt: String,
        modeTag: String,
        responseLanguage: String
    ): InferenceResult = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {

            val eng  = engine
            val conv = conversation

            if (!isInitialized || eng == null || conv == null) {
                debugLogBus.log(
                    DebugLogType.WARN, modeTag,
                    "generate() not ready — " +
                            "initialized=$isInitialized engine=${eng != null} conv=${conv != null}"
                )
                return@withContext InferenceResult.NotReady
            }

            val activeConv = if (images.isNotEmpty()) {
                try { conv.close() } catch (e: Exception) {
                    debugLogBus.log(DebugLogType.WARN, modeTag,
                        "conv.close() non-fatal: ${e.message}")
                }
                val fresh = eng.createConversation(conversationConfig())
                conversation = fresh
                fresh
            } else {
                conv
            }

            val requestId     = "$modeTag-${UUID.randomUUID().toString().take(8)}"
            val system        = localizedPrompt(baseSystemPrompt, responseLanguage, modeTag)
            val startMs       = System.currentTimeMillis()
            val scaledBitmaps = mutableListOf<Bitmap>()

            logMemory(modeTag)
            debugLogBus.log(
                DebugLogType.PROMPT_IN, modeTag,
                "requestId=$requestId lang=$responseLanguage images=${images.size}" +
                        "\nSYSTEM: $system\nUSER: $prompt",
                requestId
            )

            try {
                val contents = buildContents(prompt, system, images, scaledBitmaps)

                debugLogBus.log(
                    DebugLogType.INFO, modeTag,
                    "Calling sendMessage..." // Log adjusted
                )

                val response = withTimeoutOrNull(TIMEOUT_MS) {
                    activeConv.sendMessage(contents)?.toString()?.trim()
                }

                when {
                    response == null -> {
                        debugLogBus.log(
                            DebugLogType.ERROR, modeTag,
                            "sendMessage timed out after ${TIMEOUT_MS / 1000}s",
                            requestId
                        )
                        InferenceResult.Failure("Inference timed out")
                    }
                    response.isEmpty() -> {
                        debugLogBus.log(
                            DebugLogType.WARN, modeTag,
                            "sendMessage returned empty string",
                            requestId
                        )
                        InferenceResult.Failure("Empty response from model")
                    }
                    else -> {
                        val latency = System.currentTimeMillis() - startMs
                        debugLogBus.log(
                            DebugLogType.PROMPT_OUT, modeTag,
                            "requestId=$requestId latencyMs=$latency\nRESPONSE: $response",
                            requestId
                        )
                        InferenceResult.Success(response)
                    }
                }

            } catch (e: OutOfMemoryError) {
                debugLogBus.log(DebugLogType.ERROR, modeTag,
                    "OOM during inference — requestId=$requestId", requestId)
                System.gc()
                InferenceResult.Failure("Out of memory", RuntimeException(e))
            } catch (e: CancellationException) {
                debugLogBus.log(DebugLogType.WARN, modeTag,
                    "Inference cancelled — requestId=$requestId", requestId)
                InferenceResult.Failure("Cancelled")
            } catch (e: Exception) {
                debugLogBus.log(
                    DebugLogType.ERROR, modeTag,
                    "generate failed: ${e.javaClass.simpleName}: ${e.message}",
                    requestId
                )
                InferenceResult.Failure(e.message ?: "Generate failed", e)
            } finally {
                scaledBitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    // ── Streaming generate ────────────────────────────────────────────

    fun generateStream(
        prompt: String,
        images: List<Bitmap>     = emptyList(),
        baseSystemPrompt: String,
        modeTag: String,
        responseLanguage: String
    ): Flow<InferenceResult> = flow {
        inferenceMutex.withLock {
            val eng  = engine
            val conv = conversation

            if (!isInitialized || eng == null || conv == null) {
                debugLogBus.log(
                    DebugLogType.WARN, modeTag,
                    "generateStream() not ready — " +
                            "initialized=$isInitialized engine=${eng != null} conv=${conv != null}"
                )
                emit(InferenceResult.NotReady)
                return@withLock
            }

            val activeConv = if (images.isNotEmpty()) {
                try { conv.close() } catch (e: Exception) {
                    debugLogBus.log(DebugLogType.WARN, modeTag,
                        "conv.close() non-fatal: ${e.message}")
                }
                val fresh = eng.createConversation(conversationConfig())
                conversation = fresh
                fresh
            } else {
                conv
            }

            val requestId     = "$modeTag-${UUID.randomUUID().toString().take(8)}"
            val system        = localizedPrompt(baseSystemPrompt, responseLanguage, modeTag)
            val startMs       = System.currentTimeMillis()
            val full          = StringBuilder()
            val scaledBitmaps = mutableListOf<Bitmap>()

            logMemory(modeTag)
            debugLogBus.log(
                DebugLogType.PROMPT_IN, modeTag,
                "requestId=$requestId lang=$responseLanguage images=${images.size}" +
                        "\nSYSTEM: $system\nUSER: $prompt",
                requestId
            )

            try {
                val contents = buildContents(prompt, system, images, scaledBitmaps)

                debugLogBus.log(
                    DebugLogType.INFO, modeTag,
                    "Calling sendMessageAsync..."
                )

                activeConv.sendMessageAsync(contents).collect { token ->
                    val chunk = token?.toString() ?: return@collect
                    if (chunk.isNotEmpty()) {
                        full.append(chunk)
                        emit(InferenceResult.Streaming(chunk))
                    }
                }

                val latency = System.currentTimeMillis() - startMs
                debugLogBus.log(
                    DebugLogType.PROMPT_OUT, modeTag,
                    "requestId=$requestId latencyMs=$latency\nRESPONSE: ${full.trim()}",
                    requestId
                )

            } catch (e: OutOfMemoryError) {
                debugLogBus.log(DebugLogType.ERROR, modeTag,
                    "OOM in stream — requestId=$requestId", requestId)
                System.gc()
                emit(InferenceResult.Failure("Out of memory", RuntimeException(e)))
            } catch (e: CancellationException) {
                debugLogBus.log(DebugLogType.WARN, modeTag,
                    "Stream cancelled — requestId=$requestId", requestId)
                emit(InferenceResult.Failure("Cancelled"))
            } catch (e: Exception) {
                debugLogBus.log(
                    DebugLogType.ERROR, modeTag,
                    "stream failed: ${e.javaClass.simpleName}: ${e.message}",
                    requestId
                )
                emit(InferenceResult.Failure(e.message ?: "Stream failed", e))
            } finally {
                scaledBitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── Content building ──────────────────────────────────────────────

    private fun buildContents(
        userPrompt: String,
        systemPrompt: String,
        images: List<Bitmap>,
        scaledBitmaps: MutableList<Bitmap>
    ): Contents {
        val items = mutableListOf<Content>()

        // FIX 2 & 3: Images must be added FIRST, and LiteRT SDK expects only ONE Text object
        // to prevent segmentation faults during native decoding.

        // 1. Add Image Bytes first
        images.forEachIndexed { index, original ->
            val scaled = resized(original)
            if (scaled !== original) scaledBitmaps.add(scaled)  // track for cleanup
            val bytes = scaled.toJpeg()
            items.add(Content.ImageBytes(bytes))
            debugLogBus.log(
                DebugLogType.INFO, "GEMMA",
                "Image[$index]: ${scaled.width}×${scaled.height} → ${bytes.size / 1024}KB"
            )
        }

        // 2. Merge System Prompt and User Prompt into a single string
        val combinedPrompt = buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt)
                append("\n\n")
            }
            if (userPrompt.isNotBlank()) {
                append(userPrompt.trim())
            }
        }.trim()

        // 3. Add the unified text block
        if (combinedPrompt.isNotBlank()) {
            items.add(Content.Text(combinedPrompt))
        }

        return Contents.of(*items.toTypedArray())
    }

    // ── Image helpers ─────────────────────────────────────────────────

    private fun resized(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= IMG_MAX && h <= IMG_MAX) return bmp   // already within limit
        val scale = IMG_MAX.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            bmp,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun Bitmap.toJpeg(): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }

    // ── Prompt helpers ────────────────────────────────────────────────

    private fun localizedPrompt(base: String, lang: String, modeTag: String): String {
        val directive = when (lang.lowercase()) {
            "telugu"    -> "Respond only in Telugu using natural spoken Telugu."
            "hindi"     -> "Respond only in Hindi using Devanagari script."
            "tamil"     -> "Respond only in Tamil using natural spoken Tamil."
            "kannada"   -> "Respond only in Kannada using natural spoken Kannada."
            "malayalam" -> "Respond only in Malayalam using natural spoken Malayalam."
            else        -> "Respond only in English."
        }
        val modeRule = when (modeTag) {
            "OCR"      -> "Preserve important text exactly as seen first."
            "CURRENCY" -> "Say the denomination first."
            "NAVIGATE" -> "Prefer short directional sentences."
            else       -> ""
        }
        return "$base $directive $modeRule".trim()
    }

    // ── Memory logging ────────────────────────────────────────────────

    private fun logMemory(tag: String) {
        val rt    = Runtime.getRuntime()
        val used  = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        val max   = rt.maxMemory() / 1024 / 1024
        debugLogBus.log(DebugLogType.INFO, tag, "RAM: ${used}MB / ${max}MB")
    }

    // ── Public state ──────────────────────────────────────────────────

    /**
     * Cancel any in-flight native inference.  Safe to call from any thread.
     * No-op if nothing is running or the conversation is already closed.
     */
    fun cancelInference() {
        try {
            val conv = conversation
            if (conv != null && conv.isAlive) {
                conv.cancelProcess()
                debugLogBus.log(DebugLogType.INFO, "GEMMA", "cancelProcess() called")
            }
        } catch (e: Exception) {
            debugLogBus.log(DebugLogType.WARN, "GEMMA",
                "cancelProcess() failed: ${e.message}")
        }
    }

    fun isReady(): Boolean = isInitialized && engine != null && conversation != null

    suspend fun modelExists(): Boolean = withContext(Dispatchers.IO) {
        findModelFile() != null
    }

    fun getSearchPaths(): List<String> = listOfNotNull(
        context.filesDir?.absolutePath,
        context.getExternalFilesDir(null)?.absolutePath,
        "/storage/emulated/0/Download/",
        "/sdcard/Download/",
        "/storage/emulated/0/Android/data/com.example.feelvision/files/"
    )

    // ── Model discovery ───────────────────────────────────────────────

    private suspend fun findModelFile(): File? = withContext(Dispatchers.IO) {
        val searchDirs = listOfNotNull(
            context.filesDir,                           // internal storage
            context.getExternalFilesDir(null),           // app-scoped external
            File("/storage/emulated/0/Android/data/com.example.feelvision/files/")
        ).distinctBy { it.absolutePath }

        // Pass 1 — exact name match
        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val candidate = File(dir, MODEL_NAME)
            if (candidate.exists() && candidate.length() > 1024 * 1024) {
                debugLogBus.log(DebugLogType.OK, "GEMMA",
                    "Found by exact name in ${dir.absolutePath} (${candidate.length() / 1024 / 1024}MB)")
                return@withContext candidate
            }
        }

        // Pass 2 — fuzzy match (any .litertlm containing "gemma")
        for (dir in searchDirs) {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { file ->
                val name = file.name.lowercase()
                if (name.endsWith(".litertlm") && name.contains("gemma")
                    && file.length() > 1024 * 1024) {
                    debugLogBus.log(DebugLogType.OK, "GEMMA",
                        "Found by fuzzy match: ${file.name} in ${dir.absolutePath}")
                    return@withContext file
                }
            }
        }

        debugLogBus.log(DebugLogType.ERROR, "GEMMA", "Model not found in any search path")
        null
    }
}