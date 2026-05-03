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

class DefaultStrategy @Inject constructor(
    private val gemma: GemmaInferenceManager,
    private val settings: SettingsRepository,
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {

    override val mode          = AppMode.Default
    override val capturePolicy = CapturePolicy.SingleShot

    override fun activate() {
        log.log(DebugLogType.MODE, "DEFAULT", "Activated")
    }

    override fun deactivate() {
        log.log(DebugLogType.MODE, "DEFAULT", "Deactivated")
    }

    override suspend fun processFrame(bitmap: Bitmap): ModeResult {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            log.log(DebugLogType.ERROR, "DEFAULT", "Invalid bitmap")
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

            // ← Log BEFORE calling generate so you can see exactly where it dies
            log.log(DebugLogType.INFERENCE, "DEFAULT",
                "Calling gemma.generate — engine should be ready")

            when (val result = gemma.generate(
                prompt           = "abcd",
                images           = listOf(bitmap),
                baseSystemPrompt = ModePrompts.DEFAULT,
                modeTag          = "DEFAULT",
                responseLanguage = language
            )) {
                is InferenceResult.Success -> {
                    tts.speak(result.text)
                    ModeResult.NarrationText(result.text)
                }
                is InferenceResult.Failure -> {
                    // ← This log tells you the crash happened inside generate()
                    log.log(DebugLogType.ERROR, "DEFAULT", "generate() failed: ${result.error}")
                    tts.speak("I could not describe what I see.")
                    ModeResult.Error(result.error)
                }
                is InferenceResult.NotReady -> {
                    log.log(DebugLogType.WARN, "DEFAULT", "NotReady returned from generate()")
                    tts.speak("Model not ready.")
                    ModeResult.Error("Not ready")
                }
                else -> ModeResult.NoResult
            }
        } catch (e: Exception) {
            // ← Catches anything generate() throws that isn't caught internally
            log.log(DebugLogType.ERROR, "DEFAULT",
                "processFrame CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            tts.speak("Something went wrong.")
            ModeResult.Error(e.message ?: "Unknown error")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}