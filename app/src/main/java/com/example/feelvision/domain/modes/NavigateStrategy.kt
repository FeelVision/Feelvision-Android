// This is a stub strategy for now

package com.feelvision.domain.modes

import android.graphics.Bitmap
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.feelvision.tts.TTSManager
import javax.inject.Inject

class NavigateStrategy @Inject constructor(
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {
    override val mode          = AppMode.Navigate
    override val capturePolicy = CapturePolicy.BurstInterval(count = 3, intervalMs = 4_000L)
    override fun activate()    { tts.speak("Navigation mode. Coming in next update."); log.log(DebugLogType.MODE,"NAV","Stub activated") }
    override fun deactivate()  { }
    override suspend fun processFrame(bitmap: Bitmap) = ModeResult.NoResult
}