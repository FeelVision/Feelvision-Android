package com.feelvision.domain.modes

import android.graphics.Bitmap
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult

interface ModeStrategy {
    val mode: AppMode
    val capturePolicy: CapturePolicy
    fun activate()
    fun deactivate()
    suspend fun processFrame(bitmap: Bitmap): ModeResult

    /**
     * Process multiple frames collected by a burst capture.
     * Default implementation delegates to processFrame with the last frame.
     * Override in strategies that benefit from multi-image context (e.g. Navigate).
     */
    suspend fun processFrames(bitmaps: List<Bitmap>): ModeResult {
        return processFrame(bitmaps.last())
    }
}