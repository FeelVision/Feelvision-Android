// This is stub implementation, needs to be changed
package com.feelvision.domain.modes

import android.graphics.Bitmap
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.feelvision.tts.TTSManager
import javax.inject.Inject

class NarrateStrategy @Inject constructor(
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {
    override val mode          = AppMode.Narrate
    override val capturePolicy = CapturePolicy.Continuous(intervalMs = 4_000L)
    override fun activate()    { tts.speak("Narration mode. Coming in next update."); log.log(DebugLogType.MODE,"NAR","Stub activated") }
    override fun deactivate()  { }
    override suspend fun processFrame(bitmap: Bitmap) = ModeResult.NoResult
}