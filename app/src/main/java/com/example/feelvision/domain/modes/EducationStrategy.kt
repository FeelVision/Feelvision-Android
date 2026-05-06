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
import kotlin.coroutines.cancellation.CancellationException

class EducationStrategy @Inject constructor(
    private val gemma: GemmaInferenceManager,
    private val settings: SettingsRepository,
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {

    override val mode          = AppMode.Edu
    override val capturePolicy = CapturePolicy.SingleShot

    override fun activate() {
        log.log(DebugLogType.MODE, "EDU", "Activated — single-shot capture")
    }

    override fun deactivate() {
        log.log(DebugLogType.MODE, "EDU", "Deactivated")
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
            log.log(DebugLogType.ERROR, "EDU", "Invalid bitmap")
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

            log.log(DebugLogType.INFERENCE, "EDU",
                "Calling gemma.generateStream — streaming mode")

            val sentenceBuffer = StringBuilder()
            val fullText = StringBuilder()
            var hadError: InferenceResult.Failure? = null

            gemma.generateStream(
                prompt           = userPrompt ?: "Explain what you see in this image in an educational way.",
                images           = listOf(bitmap),
                baseSystemPrompt = ModePrompts.EDU,
                modeTag          = "EDU",
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
                log.log(DebugLogType.ERROR, "EDU", "stream failed: ${hadError!!.error}")
                tts.speak("I could not explain what I see.")
                ModeResult.Error(hadError!!.error)
            } else if (fullText.isNotEmpty()) {
                ModeResult.EduContent(fullText.toString().trim())
            } else {
                tts.speak("I could not explain what I see.")
                ModeResult.Error("Empty response")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "EDU",
                "processFrameStreaming CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            tts.speak("Something went wrong in education mode.")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}