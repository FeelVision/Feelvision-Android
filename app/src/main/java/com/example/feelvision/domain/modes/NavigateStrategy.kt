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

class NavigateStrategy @Inject constructor(
    private val gemma: GemmaInferenceManager,
    private val settings: SettingsRepository,
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {

    override val mode          = AppMode.Navigate
    override val capturePolicy = CapturePolicy.SingleShot

    override fun activate() {
        log.log(DebugLogType.MODE, "NAV", "Activated — single-shot capture")
    }

    override fun deactivate() {
        log.log(DebugLogType.MODE, "NAV", "Deactivated")
    }

    /**
     * Single-frame fallback — used if CaptureEngine calls processFrame directly.
     */
    override suspend fun processFrame(bitmap: Bitmap): ModeResult {
        return processFramesStreaming(listOf(bitmap)) { /* no UI callback */ }
    }

    /**
     * Primary entry: delegates to processFramesStreaming with a no-op UI callback.
     */
    override suspend fun processFrames(bitmaps: List<Bitmap>): ModeResult {
        return processFramesStreaming(bitmaps) { /* no UI callback */ }
    }

    override suspend fun processFrameStreaming(
        bitmap: Bitmap,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        return processFramesStreaming(listOf(bitmap), onChunk)
    }

    override suspend fun processFramesStreaming(
        bitmaps: List<Bitmap>,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        if (bitmaps.isEmpty()) {
            log.log(DebugLogType.ERROR, "NAV", "No frames to process")
            return ModeResult.Error("No frames")
        }

        val valid = bitmaps.filter { !it.isRecycled && it.width > 0 && it.height > 0 }
        if (valid.isEmpty()) {
            log.log(DebugLogType.ERROR, "NAV", "All ${bitmaps.size} frames are invalid")
            return ModeResult.Error("All frames invalid")
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

            log.log(DebugLogType.INFERENCE, "NAV",
                "Streaming ${valid.size} frames for navigation guidance")

            val sentenceBuffer = StringBuilder()
            val fullText = StringBuilder()
            var hadError: InferenceResult.Failure? = null

            gemma.generateStream(
                prompt           = "Analyze these ${valid.size} sequential images and guide me.",
                images           = valid,
                baseSystemPrompt = ModePrompts.NAVIGATE,
                modeTag          = "NAVIGATE",
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
                log.log(DebugLogType.ERROR, "NAV", "stream failed: ${hadError!!.error}")
                tts.speak("I could not assess the path ahead.")
                ModeResult.Error(hadError!!.error)
            } else if (fullText.isNotEmpty()) {
                ModeResult.NavigationInstruction(
                    instruction = fullText.toString().trim(),
                    distanceM   = 0f
                )
            } else {
                tts.speak("I could not assess the path ahead.")
                ModeResult.Error("Empty response")
            }
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "NAV",
                "processFramesStreaming CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            tts.speak("Something went wrong with navigation.")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}