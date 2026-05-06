package com.feelvision.luckfox

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

import com.feelvision.hardware.LuckfoxBridge
import com.feelvision.hardware.HardwareCommand

class LuckfoxTcpServer(
    private val port: Int = 8065,
    private val frameWidth: Int = 2304,
    private val frameHeight: Int = 1296
) : LuckfoxBridge {
    private val _receivedImages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 5)
    override val receivedImages: SharedFlow<ByteArray> = _receivedImages.asSharedFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    companion object {
        private const val TAG = "LuckfoxTcpServer"

        fun getLocalIpAddresses(): List<String> {
            val addresses = mutableListOf<String>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addrs = networkInterface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is InetAddress && !addr.isLoopbackAddress) {
                            val hostAddr = addr.hostAddress ?: continue
                            if (!hostAddr.contains(':')) {
                                val name = networkInterface.displayName ?: networkInterface.name
                                addresses.add("$hostAddr ($name)")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (addresses.isEmpty()) {
                addresses.add("No network interfaces found")
            }
            return addresses
        }
    }

    enum class ServerState {
        STOPPED, STARTING, LISTENING, CONNECTED, ERROR
    }

    data class ServerStatus(
        val state: ServerState = ServerState.STOPPED,
        val message: String = "Server stopped",
        val clientAddress: String? = null,
        val imageCount: Int = 0,
        val listenAddresses: List<String> = emptyList()
    )

    private val _status = MutableStateFlow(ServerStatus())
    override val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null
    private var clientOutputStream: DataOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun log(msg: String) {
        Log.i(TAG, msg)
        _logs.tryEmit(msg)
    }

    override fun start() {
        if (serverJob?.isActive == true) return

        log("=== SERVER START REQUESTED ===")
        serverJob = scope.launch {
            try {
                _status.value = ServerStatus(state = ServerState.STARTING, message = "Starting server...")
                serverSocket = ServerSocket(port, 1).also { it.reuseAddress = true }
                val addresses = getLocalIpAddresses()
                _status.value = ServerStatus(
                    state = ServerState.LISTENING,
                    message = "Listening on port $port",
                    listenAddresses = addresses
                )
                log("Listening on port $port. Addresses: ${addresses.joinToString()}")

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        handleClient(socket)
                    } catch (e: SocketException) {
                        if (isActive) {
                            _status.value = _status.value.copy(
                                state = ServerState.ERROR,
                                message = "Socket error: ${e.message}"
                            )
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                log("Server error: ${e.message}")
                if (isActive) {
                    _status.value = ServerStatus(state = ServerState.ERROR, message = "Error: ${e.message}")
                }
            } finally {
                cleanup()
                if (_status.value.state != ServerState.ERROR) {
                    _status.value = ServerStatus(state = ServerState.STOPPED, message = "Server stopped")
                }
                log("=== SERVER STOPPED ===")
            }
        }
    }

    override fun stop() {
        serverJob?.cancel()
        cleanup()
        _status.value = ServerStatus(state = ServerState.STOPPED, message = "Server stopped")
    }

    /**
     * Sends a command to the connected Luckfox client.
     */
    override fun sendCommand(command: HardwareCommand) {
        val output = clientOutputStream ?: return
        scope.launch {
            try {
                when (command) {
                    is HardwareCommand.Capture -> {
                        output.writeInt(1) // Command type 1: Capture
                        val longFrameId = command.frameId.toLongOrNull() ?: command.frameId.hashCode().toLong()
                        output.writeLong(longFrameId)
                    }
                    else -> {
                        // Ignore or add simple reset packet (0) for non-captures if needed
                        output.writeInt(0)
                    }
                }
                output.flush()
                log("Sent command: $command")
            } catch (e: Exception) {
                log("Error sending command: ${e.message}")
            }
        }
    }

    private fun cleanup() {
        try { clientOutputStream?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        clientOutputStream = null
        clientSocket = null
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        clientSocket = socket
        val clientAddr = "${socket.inetAddress.hostAddress}:${socket.port}"
        log("Connected: $clientAddr")

        _status.value = _status.value.copy(
            state = ServerState.CONNECTED,
            message = "Connected: $clientAddr",
            clientAddress = clientAddr
        )

        var imageCount = _status.value.imageCount

        try {
            val input = DataInputStream(socket.getInputStream())
            clientOutputStream = DataOutputStream(socket.getOutputStream())

            while (currentCoroutineContext().isActive && !socket.isClosed) {
                val imageSize: Int
                try {
                    imageSize = input.readInt()
                } catch (e: Exception) {
                    break
                }

                if (imageSize <= 0 || imageSize > 50_000_000) continue

                log("Receiving image $imageCount ($imageSize bytes)...")
                val imageData = ByteArray(imageSize)
                var bytesRead = 0

                while (bytesRead < imageSize) {
                    val n = input.read(imageData, bytesRead, imageSize - bytesRead)
                    if (n < 0) break
                    bytesRead += n
                }

                if (bytesRead == imageSize) {
                    val jpegData = ImageConverter.nv21ToJpeg(imageData, frameWidth, frameHeight)
                    if (jpegData != null) {
                        imageCount++
                        _status.value = _status.value.copy(
                            message = "Received image $imageCount",
                            imageCount = imageCount
                        )
                        log("Image received and converted to JPEG (${jpegData.size} bytes)")
                        _receivedImages.tryEmit(jpegData)
                    } else {
                        log("Conversion failed for image $imageCount")
                    }
                } else {
                    log("Partial image received")
                    break
                }
            }
        } catch (e: Exception) {
            log("Client error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
            clientSocket = null
            _status.value = _status.value.copy(
                state = ServerState.LISTENING,
                message = "Client disconnected. Waiting for reconnection...",
                clientAddress = null
            )
            log("Client disconnected: $clientAddr")
        }
    }
}
