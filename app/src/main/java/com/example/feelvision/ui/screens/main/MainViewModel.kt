package com.feelvision.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.domain.coordinator.ModeCoordinator
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.model.ModeResult
import com.feelvision.hardware.HardwareSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.feelvision.tts.TTSManager
import com.feelvision.domain.button.ButtonHandler
import com.feelvision.domain.model.PhysicalButton
import com.feelvision.inference.GemmaInferenceManager

import android.graphics.Bitmap

data class MainUiState(
    val currentMode: AppMode = AppMode.Default,
    val statusText: String = "Ready",
    val isListening: Boolean = false,
    val luckfoxConnected: Boolean = false,
    val lastResult: ModeResult? = null,
    val isInferring: Boolean = false,
    val gemmaReady: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val burstProgress: String? = null,   // e.g. "3/5" during burst capture
)

sealed class MainIntent {
    data class SwitchMode(val mode: AppMode) : MainIntent()
    data object Capture : MainIntent()
    data object ScanModel : MainIntent()
    data object DismissResult : MainIntent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val coordinator: ModeCoordinator,
    private val hardware: HardwareSource,
    private val tts: TTSManager,
    private val buttonHandler: ButtonHandler,
    private val gemma: GemmaInferenceManager
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.activeModeFlow.collect { mode ->
                _state.update { it.copy(currentMode = mode, statusText = mode.getLocalizedAnnouncement(tts.currentLanguageTag)) }
            }
        }
        viewModelScope.launch {
            hardware.connectionStatus.collect { status ->
                _state.update { it.copy(luckfoxConnected = !status.contains("Debug")) }
            }
        }
        // Sync model ready state
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(gemmaReady = gemma.isReady()) }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.SwitchMode -> viewModelScope.launch { coordinator.switchTo(intent.mode) }
            is MainIntent.Capture -> {
                if (_state.value.isInferring || _state.value.burstProgress != null) return
                viewModelScope.launch {
                    if (!gemma.isReady()) {
                        _state.update { it.copy(statusText = "Model not ready") }
                        tts.speak("Model not ready yet.")
                        return@launch
                    }

                    val strategy = coordinator.activeStrategy
                    val policy   = strategy.capturePolicy

                    when (policy) {
                        is CapturePolicy.SingleShot    -> executeSingleCapture()
                        is CapturePolicy.BurstInterval -> executeBurstCapture(policy)
                        is CapturePolicy.Continuous    -> {
                            _state.update { it.copy(statusText = "Continuous mode — auto-capturing") }
                        }
                        is CapturePolicy.None          -> {
                            _state.update { it.copy(statusText = "No capture for this mode") }
                        }
                    }
                }
            }
            is MainIntent.ScanModel -> viewModelScope.launch { gemma.initialize() }
            is MainIntent.DismissResult -> {
                _state.value.capturedBitmap?.recycle()
                _state.update { it.copy(capturedBitmap = null, lastResult = null) }
            }
        }
    }

    // ── Single-shot capture (Default, OCR, Currency, etc.) ──────────────

    private suspend fun executeSingleCapture() {
        _state.update { it.copy(isInferring = true, statusText = "Capturing...", capturedBitmap = null) }
        val bmp = hardware.captureNow()
        if (bmp != null) {
            // Create a copy for the UI to prevent crash if strategy recycles original
            val displayBmp = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
            _state.update { it.copy(statusText = "Analyzing...", capturedBitmap = displayBmp) }
            try {
                val result = coordinator.activeStrategy.processFrame(bmp)
                _state.update { it.copy(lastResult = result, statusText = "Ready") }
            } catch (e: Exception) {
                _state.update { it.copy(statusText = "Analysis failed") }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        } else {
            _state.update { it.copy(statusText = "Capture failed") }
        }
        _state.update { it.copy(isInferring = false) }
    }

    // ── Burst capture (Navigate) ────────────────────────────────────────

    private suspend fun executeBurstCapture(policy: CapturePolicy.BurstInterval) {
        val frames = mutableListOf<Bitmap>()
        _state.update { it.copy(
            burstProgress = "0/${policy.count}",
            statusText = "Burst capturing...",
            capturedBitmap = null
        ) }

        try {
            repeat(policy.count) { i ->
                val bmp = hardware.captureNow()
                if (bmp == null) {
                    _state.update { it.copy(statusText = "Burst capture failed") }
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    return
                }
                frames.add(bmp)
                _state.update { it.copy(
                    burstProgress = "${i + 1}/${policy.count}",
                    statusText = "Capturing ${i + 1} of ${policy.count}..."
                ) }

                // Show the latest frame as preview
                val displayBmp = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
                _state.value.capturedBitmap?.recycle()
                _state.update { it.copy(capturedBitmap = displayBmp) }

                // Wait between captures (except after the last one)
                if (i < policy.count - 1) {
                    kotlinx.coroutines.delay(policy.intervalMs)
                }
            }

            // All frames captured — now infer
            _state.update { it.copy(
                burstProgress = null,
                isInferring = true,
                statusText = "Analyzing ${frames.size} frames..."
            ) }

            try {
                val result = coordinator.activeStrategy.processFrames(frames)
                _state.update { it.copy(lastResult = result, statusText = "Ready") }
            } catch (e: Exception) {
                _state.update { it.copy(statusText = "Analysis failed") }
            }
        } finally {
            _state.update { it.copy(burstProgress = null, isInferring = false) }
        }
    }
}