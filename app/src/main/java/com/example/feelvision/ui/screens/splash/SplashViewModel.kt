// ui/screens/splash/SplashViewModel.kt — NEW
package com.feelvision.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.domain.coordinator.ModeCoordinator
import com.feelvision.domain.model.AppMode
import com.feelvision.hardware.HardwareSource
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.tts.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(
    val statusMessage: String = "Starting up...",
    val progressTarget: Float = 0f,
    val initComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val hardware: HardwareSource,
    private val coordinator: ModeCoordinator,
    private val gemma: GemmaInferenceManager,
    private val tts: TTSManager
) : ViewModel() {

    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            try {
                // Step 1 — Hardware
                _state.update { it.copy(statusMessage = "Starting hardware...", progressTarget = 0.2f) }
                hardware.initialize()

                // Step 2 — Default mode (explicitly set so coordinator state is correct)
                _state.update { it.copy(statusMessage = "Setting default mode...", progressTarget = 0.4f) }
                coordinator.switchTo(AppMode.Default)

                // Step 3 — TTS
                _state.update { it.copy(statusMessage = "Initializing voice...", progressTarget = 0.6f) }
                // TTSManager initializes in its own constructor — just wait a beat
                kotlinx.coroutines.delay(300)

                // Step 4 — Gemma (non-blocking: model loads in background,
                // main screen shows a pill while it finishes)
                _state.update { it.copy(statusMessage = "Preparing model...", progressTarget = 0.8f) }
                // We fire gemma.initialize() in the background —
                // it can take 10-30s but the splash proceeds immediately.
                // Main screen shows a status pill while it finishes.
                viewModelScope.launch { gemma.initialize() }


                // Step 5 — Done
                _state.update { it.copy(
                    statusMessage = "Ready",
                    progressTarget = 1f,
                    initComplete = true
                ) }

            } catch (e: Exception) {
                _state.update { it.copy(
                    statusMessage = "Ready (partial init)",
                    errorMessage = e.message?.take(60),
                    progressTarget = 1f,
                    initComplete = true   // still proceed to main
                ) }
            }
        }
    }
}