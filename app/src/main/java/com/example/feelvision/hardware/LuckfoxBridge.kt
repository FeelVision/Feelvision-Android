package com.feelvision.hardware

import com.feelvision.domain.model.ButtonEvent
import com.feelvision.luckfox.LuckfoxTcpServer
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for communicating with the Luckfox hardware / smart glasses.
 */
interface LuckfoxBridge {
    /** Stream of images received from the hardware. */
    val receivedImages: SharedFlow<ByteArray>

    /** Stream of button events received from the hardware. */
    val buttonEvents: SharedFlow<ButtonEvent>

    /** Current status of the hardware connection. */
    val status: StateFlow<LuckfoxTcpServer.ServerStatus>
    
    /** Sends a command to the hardware. */
    fun sendCommand(command: HardwareCommand)
    
    /** Starts the communication bridge. */
    fun start()
    
    /** Stops the communication bridge. */
    fun stop()
}

