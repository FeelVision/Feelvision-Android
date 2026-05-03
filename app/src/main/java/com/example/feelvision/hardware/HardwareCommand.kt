package com.feelvision.hardware

import com.feelvision.domain.model.AppMode

sealed class HardwareCommand {
    data object ActivateCamera : HardwareCommand()
    data object DeactivateCamera : HardwareCommand()
    data class  SetMode(val mode: AppMode) : HardwareCommand()
    data class  Capture(val frameId: String) : HardwareCommand()
    data object CancelBurst : HardwareCommand()
    data object Shutdown : HardwareCommand()
    data class  SetResolution(val width: Int, val height: Int) : HardwareCommand()
}