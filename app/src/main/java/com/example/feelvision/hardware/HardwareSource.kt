package com.feelvision.hardware

import android.graphics.Bitmap
import com.feelvision.domain.model.ButtonEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface HardwareSource {
    val isReady: StateFlow<Boolean>
    val connectionStatus: StateFlow<String>
    val rawLogFlow: SharedFlow<String>
    val buttonEvents: SharedFlow<ButtonEvent>
    val frameFlow: SharedFlow<Bitmap>
    suspend fun initialize()
    suspend fun sendCommand(command: HardwareCommand)
    suspend fun captureNow(): Bitmap?
    fun release()
}