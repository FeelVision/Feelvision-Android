package com.feelvision.ui.screens.debug

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.domain.button.ButtonHandler
import com.feelvision.domain.coordinator.ModeCoordinator
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.PhysicalButton
import com.feelvision.hardware.HardwareSource
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.logging.DebugLogBus
import com.feelvision.logging.DebugLogType
import com.feelvision.speech.SpeechRecognitionManager
import com.feelvision.tts.TTSManager
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
    val burstProgress: String?  = null,   // e.g. "3/5" during burst capture
    val isListeningForMode: Boolean = false,
    val streamingText: String   = "",     // incrementally built text during streaming inference
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val coordinator: ModeCoordinator,
    private val hardware: HardwareSource,
    private val buttonHandler: ButtonHandler,
    private val debugLogBus: DebugLogBus,
    private val gemma: GemmaInferenceManager,
    private val speechRecognizer: SpeechRecognitionManager,
    private val tts: TTSManager
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
        // Listen to speech recognition results
        viewModelScope.launch {
            speechRecognizer.isListening.collect { listening ->
                _state.update { it.copy(isListeningForMode = listening) }
            }
        }
        viewModelScope.launch {
            speechRecognizer.error.collect { error ->
                appendLog("[ERR] Speech recognition: $error")
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

    fun startVoiceModeSwitch() {
        appendLog("[CMD] Voice mode switch activated...")
        speechRecognizer.startListening()
    }

    /**
     * Mode-aware capture:
     * - SingleShot modes (Default, OCR, etc.) → capture 1 image → processFrameStreaming
     * - BurstInterval modes (Navigate)        → capture N images at interval → processFramesStreaming
     */
    fun captureNow() {
        if (_state.value.isInferring || _state.value.burstProgress != null) {
            appendLog("[WARN] Capture already running — ignoring")
            return
        }
        viewModelScope.launch {
            val strategy = coordinator.activeStrategy
            val policy   = strategy.capturePolicy

            when (policy) {
                is CapturePolicy.SingleShot -> executeSingleCapture()
                is CapturePolicy.BurstInterval -> executeBurstCapture(policy)
                is CapturePolicy.Continuous -> {
                    appendLog("[WARN] Continuous mode — use CaptureEngine instead")
                }
                is CapturePolicy.None -> {
                    appendLog("[WARN] Current mode has no capture policy")
                }
            }
        }
    }

    // ── Single-shot capture (OCR, Default, etc.) ────────────────────────

    private suspend fun executeSingleCapture() {
        appendLog("[CMD] Single capture requested (${coordinator.activeMode.displayName})")
        _state.update { it.copy(streamingText = "") }
        val bmp = hardware.captureNow()
        if (bmp != null) {
            appendLog("[OK] Frame: ${bmp.width}×${bmp.height}")
            if (gemma.isReady()) {
                appendLog("[CMD] Streaming with ${coordinator.activeMode.shortLabel} strategy...")
                _state.update { it.copy(isInferring = true) }
                
                tts.playBeep()
                val prompt = speechRecognizer.waitForSpeech()
                appendLog("[PROMPT] $prompt")

                try {
                    coordinator.activeStrategy.processFrameStreaming(bmp, prompt) { chunk ->
                        _state.update { it.copy(streamingText = it.streamingText + chunk + " ") }
                        appendLog("[STREAM] $chunk")
                    }
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

    // ── Burst capture (Navigate) ────────────────────────────────────────

    private suspend fun executeBurstCapture(policy: CapturePolicy.BurstInterval) {
        appendLog("[CMD] Burst capture: ${policy.count} frames @ ${policy.intervalMs}ms interval")
        _state.update { it.copy(streamingText = "") }

        val frames = mutableListOf<Bitmap>()
        _state.update { it.copy(burstProgress = "0/${policy.count}") }

        try {
            repeat(policy.count) { i ->
                appendLog("[CMD] Capturing frame ${i + 1}/${policy.count}...")
                val bmp = hardware.captureNow()
                if (bmp == null) {
                    appendLog("[ERR] Burst capture ${i + 1} returned null — aborting")
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    return
                }
                frames.add(bmp)
                _state.update { it.copy(burstProgress = "${i + 1}/${policy.count}") }
                appendLog("[OK] Frame ${i + 1}: ${bmp.width}×${bmp.height}")

                // Wait between captures (except after the last one)
                if (i < policy.count - 1) {
                    kotlinx.coroutines.delay(policy.intervalMs)
                }
            }

            appendLog("[CMD] All ${frames.size} frames captured — streaming to ${coordinator.activeMode.shortLabel} strategy...")
            _state.update { it.copy(burstProgress = null, isInferring = true) }

            if (gemma.isReady()) {
                tts.playBeep()
                val prompt = speechRecognizer.waitForSpeech()
                appendLog("[PROMPT] $prompt")

                try {
                    coordinator.activeStrategy.processFramesStreaming(frames, prompt) { chunk ->
                        _state.update { it.copy(streamingText = it.streamingText + chunk + " ") }
                        appendLog("[STREAM] $chunk")
                    }
                } finally {
                    _state.update { it.copy(isInferring = false) }
                }
            } else {
                appendLog("[WARN] Gemma not ready — discarding ${frames.size} frames")
                frames.forEach { if (!it.isRecycled) it.recycle() }
            }
        } finally {
            _state.update { it.copy(burstProgress = null, isInferring = false) }
        }
    }

    private fun appendLog(line: String) {
        _state.update { s -> s.copy(logs = (s.logs + line).takeLast(300)) }
    }
}