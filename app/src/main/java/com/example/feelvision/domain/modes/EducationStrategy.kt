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

    override suspend fun processFrame(bitmap: Bitmap): ModeResult {
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
                "Processing single frame ${bitmap.width}×${bitmap.height}")

            when (val result = gemma.generate(
                prompt           = "Explain what you see in this image in an educational way.",
                images           = listOf(bitmap),
                baseSystemPrompt = ModePrompts.EDU,
                modeTag          = "EDU",
                responseLanguage = language
            )) {
                is InferenceResult.Success -> {
                    log.log(DebugLogType.OK, "EDU", "Education: ${result.text.take(80)}...")
                    tts.speak(result.text)
                    ModeResult.EduContent(result.text)
                }
                is InferenceResult.Failure -> {
                    log.log(DebugLogType.ERROR, "EDU", "generate() failed: ${result.error}")
                    tts.speak("I could not explain what I see.")
                    ModeResult.Error(result.error)
                }
                is InferenceResult.NotReady -> {
                    log.log(DebugLogType.WARN, "EDU", "NotReady returned from generate()")
                    tts.speak("Model not ready.")
                    ModeResult.Error("Not ready")
                }
                else -> ModeResult.NoResult
            }
        } catch (e: Exception) {
            log.log(DebugLogType.ERROR, "EDU",
                "processFrame CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            tts.speak("Something went wrong in education mode.")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}