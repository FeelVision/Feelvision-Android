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

    /**
     * Stream-process a single frame: emits partial text chunks via [onChunk]
     * as they are generated, enabling immediate TTS playback.
     * Default falls back to non-streaming [processFrame].
     */
    suspend fun processFrameStreaming(
        bitmap: Bitmap,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        return processFrame(bitmap)
    }

    /**
     * Stream-process multiple frames: emits partial text chunks via [onChunk].
     * Default falls back to non-streaming [processFrames].
     */
    suspend fun processFramesStreaming(
        bitmaps: List<Bitmap>,
        onChunk: suspend (String) -> Unit
    ): ModeResult {
        return processFrames(bitmaps)
    }
}