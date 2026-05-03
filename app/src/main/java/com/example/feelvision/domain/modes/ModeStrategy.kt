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
}