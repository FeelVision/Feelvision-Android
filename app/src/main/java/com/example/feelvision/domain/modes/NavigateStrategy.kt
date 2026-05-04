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
        return processFrames(listOf(bitmap))
    }

    /**
     * Primary entry: receives all burst-captured frames and sends them
     * together to Gemma for directional navigation guidance.
     */
    override suspend fun processFrames(bitmaps: List<Bitmap>): ModeResult {
        if (bitmaps.isEmpty()) {
            log.log(DebugLogType.ERROR, "NAV", "No frames to process")
            return ModeResult.Error("No frames")
        }

        // Validate all bitmaps
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
                "Processing ${valid.size} frames for navigation guidance")

            when (val result = gemma.generate(
                prompt           = "Analyze these ${valid.size} sequential images and guide me.",
                images           = valid,
                baseSystemPrompt = ModePrompts.NAVIGATE,
                modeTag          = "NAVIGATE",
                responseLanguage = language
            )) {
                is InferenceResult.Success -> {
                    log.log(DebugLogType.OK, "NAV",
                        "Navigation: ${result.text.take(80)}...")
                    tts.speak(result.text)
                    ModeResult.NavigationInstruction(
                        instruction = result.text,
                        distanceM   = 0f  // no distance estimation from vision alone
                    )
                }
                is InferenceResult.Failure -> {
                    log.log(DebugLogType.ERROR, "NAV", "generate() failed: ${result.error}")
                    tts.speak("I could not assess the path ahead.")
                    ModeResult.Error(result.error)
                }
                is InferenceResult.NotReady -> {
                    log.log(DebugLogType.WARN, "NAV", "NotReady returned from generate()")
                    tts.speak("Model not ready.")
                    ModeResult.Error("Not ready")
                }
                else -> ModeResult.NoResult
            }
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "NAV",
                "processFrames CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            tts.speak("Something went wrong with navigation.")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}