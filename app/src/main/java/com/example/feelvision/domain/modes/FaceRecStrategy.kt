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

class FaceRecStrategy @Inject constructor(
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {
    override val mode          = AppMode.Face
    override val capturePolicy = CapturePolicy.Continuous(intervalMs = 3_000L)
    override fun activate()    { tts.speak("People mode. Coming in next update."); log.log(DebugLogType.MODE,"FACE","Stub activated") }
    override fun deactivate()  { }
    override suspend fun processFrame(bitmap: Bitmap, userPrompt: String?) = ModeResult.NoResult
}