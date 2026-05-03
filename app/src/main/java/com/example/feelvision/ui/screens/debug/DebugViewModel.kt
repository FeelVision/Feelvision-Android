package com.feelvision.ui.screens.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.domain.button.ButtonHandler
import com.feelvision.domain.coordinator.ModeCoordinator
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.PhysicalButton
import com.feelvision.hardware.HardwareSource
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugUiState(
    val currentMode: AppMode    = AppMode.Default,
    val connectionLabel: String = "",
    val logs: List<String>      = emptyList(),
    val isInferring: Boolean    = false,
    val gemmaReady: Boolean     = false,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val coordinator: ModeCoordinator,
    private val hardware: HardwareSource,
    private val buttonHandler: ButtonHandler,
    private val debugLogBus: DebugLogBus,
    private val gemma: GemmaInferenceManager
) : ViewModel() {

    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.activeModeFlow.collect { mode ->
                _state.update { it.copy(currentMode = mode) }
            }
        }
        viewModelScope.launch {
            hardware.connectionStatus.collect { status ->
                _state.update { it.copy(connectionLabel = status) }
            }
        }
        viewModelScope.launch {
            hardware.rawLogFlow.collect { line ->
                _state.update { s -> s.copy(logs = (s.logs + line).takeLast(300)) }
            }
        }
        viewModelScope.launch {
            debugLogBus.logs.collect { entry ->
                val prefix = when (entry.type) {
                    DebugLogType.ERROR   -> "[ERR]"
                    DebugLogType.OK      -> "[OK]"
                    DebugLogType.WARN    -> "[WARN]"
                    DebugLogType.COMMAND -> "[CMD]"
                    DebugLogType.BUTTON  -> "[BTN]"
                    else                 -> "[INFO]"
                }
                appendLog("$prefix ${entry.tag}: ${entry.message}")
            }
        }
        // Reflect gemma ready state into UI
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(gemmaReady = gemma.isReady()) }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    fun triggerMode(mode: AppMode) {
        viewModelScope.launch {
            coordinator.switchTo(mode)
            appendLog("[CMD] Manual switch → Mode ${mode.id} (${mode.displayName})")
        }
    }

    fun simulateButton(button: PhysicalButton, type: String = "short") {
        buttonHandler.simulatePress(button, type)
        appendLog("[BTN] Simulate $type press → Button $button")
    }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    fun captureNow() {
        if (_state.value.isInferring) {
            appendLog("[WARN] Inference already running — ignoring capture")
            return
        }
        viewModelScope.launch {
            appendLog("[CMD] Manual capture requested")
            val bmp = hardware.captureNow()
            if (bmp != null) {
                appendLog("[OK] Frame: ${bmp.width}×${bmp.height}")
                if (gemma.isReady()) {
                    appendLog("[CMD] Processing with ${coordinator.activeStrategy.mode} strategy...")
                    _state.update { it.copy(isInferring = true) }
                    try {
                        coordinator.activeStrategy.processFrame(bmp)
                    } finally {
                        _state.update { it.copy(isInferring = false) }
                    }
                } else {
                    appendLog("[WARN] Gemma not ready")
                }
            } else {
                appendLog("[ERR] Capture returned null")
            }
        }
    }

    private fun appendLog(line: String) {
        _state.update { s -> s.copy(logs = (s.logs + line).takeLast(300)) }
    }
}