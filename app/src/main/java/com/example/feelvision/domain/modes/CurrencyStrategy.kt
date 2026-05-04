package com.feelvision.domain.modes

import android.graphics.Bitmap
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.inference.InferenceResult
import com.feelvision.inference.ModePrompts
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.feelvision.tts.TTSManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CurrencyStrategy @Inject constructor(
    private val gemma: GemmaInferenceManager,
    private val settings: SettingsRepository,
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {

    override val mode          = AppMode.Currency
    override val capturePolicy = CapturePolicy.SingleShot

    override fun activate() {
        log.log(DebugLogType.MODE, "CUR", "Activated — single-shot capture")
    }

    override fun deactivate() {
        log.log(DebugLogType.MODE, "CUR", "Deactivated")
    }

    override suspend fun processFrame(bitmap: Bitmap, userPrompt: String?): ModeResult {
        return processFrameStreaming(bitmap, userPrompt) { /* no UI callback when called via processFrame */ }
    }

    override suspend fun processFrameStreaming(
        bitmap: Bitmap,
        userPrompt: String?,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            log.log(DebugLogType.ERROR, "CUR", "Invalid bitmap")
            return ModeResult.Error("Invalid bitmap")
        }

        return try {
            val language = try {
                settings.language.first()
            } catch (e: Exception) {
                "English"
            }

            if (!gemma.isReady()) {
                tts.speak("Model not ready yet.")
                return ModeResult.Error("Model not ready")
            }

            log.log(DebugLogType.INFERENCE, "CUR",
                "Calling gemma.generateStream — streaming mode")

            val sentenceBuffer = StringBuilder()
            val fullText = StringBuilder()
            var hadError: InferenceResult.Failure? = null

            gemma.generateStream(
                prompt           = userPrompt ?: "Identify the currency note in this image.",
                images           = listOf(bitmap),
                baseSystemPrompt = ModePrompts.CURRENCY,
                modeTag          = "CURRENCY",
                responseLanguage = language
            ).collect { result ->
                when (result) {
                    is InferenceResult.Streaming -> {
                        fullText.append(result.partial)
                        sentenceBuffer.append(result.partial)
                        val text = sentenceBuffer.toString()
                        val lastBoundary = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                        if (lastBoundary >= 0) {
                            val toSpeak = text.substring(0, lastBoundary + 1).trim()
                            if (toSpeak.isNotEmpty()) {
                                tts.speakChunk(toSpeak)
                                onChunk(toSpeak)
                            }
                            sentenceBuffer.clear()
                            sentenceBuffer.append(text.substring(lastBoundary + 1))
                        }
                    }
                    is InferenceResult.Failure -> { hadError = result }
                    is InferenceResult.NotReady -> { hadError = InferenceResult.Failure("Not ready") }
                    else -> {}
                }
            }

            val remaining = sentenceBuffer.toString().trim()
            if (remaining.isNotEmpty()) {
                tts.speakChunk(remaining)
                onChunk(remaining)
            }

            if (hadError != null) {
                log.log(DebugLogType.ERROR, "CUR", "stream failed: ${hadError!!.error}")
                tts.speak("I could not identify the currency.")
                ModeResult.Error(hadError!!.error)
            } else if (fullText.isNotEmpty()) {
                ModeResult.CurrencyDetected(
                    denomination = fullText.toString().trim(),
                    series       = "",
                    confidence   = 1f
                )
            } else {
                tts.speak("I could not identify the currency.")
                ModeResult.Error("Empty response")
            }
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "CUR",
                "processFrameStreaming CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            tts.speak("Something went wrong identifying currency.")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}