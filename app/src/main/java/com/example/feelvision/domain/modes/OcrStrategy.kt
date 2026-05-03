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

class OcrStrategy @Inject constructor(
    private val tts: TTSManager,
    private val log: DebugLogBus
) : ModeStrategy {
    override val mode          = AppMode.OCR
    override val capturePolicy = CapturePolicy.SingleShot
    override fun activate()    { tts.speak("Read text mode. Coming in next update."); log.log(DebugLogType.MODE,"OCR","Stub activated") }
    override fun deactivate()  { }
    override suspend fun processFrame(bitmap: Bitmap) = ModeResult.NoResult
}