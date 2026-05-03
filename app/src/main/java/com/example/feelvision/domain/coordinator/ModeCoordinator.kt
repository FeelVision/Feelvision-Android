package com.feelvision.domain.coordinator

import com.feelvision.domain.capture.CaptureEngine
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.modes.ModeStrategy
import com.feelvision.hardware.HardwareCommand
import com.feelvision.hardware.HardwareSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModeCoordinator @Inject constructor(
    private val strategies: Map<AppMode, @JvmSuppressWildcards ModeStrategy>,
    private val captureEngine: CaptureEngine,
    private val hardware: HardwareSource
) {
    private val _activeModeFlow = MutableStateFlow<AppMode>(AppMode.Default)
    val activeModeFlow: StateFlow<AppMode> = _activeModeFlow.asStateFlow()

    val activeMode: AppMode     get() = _activeModeFlow.value
    val activeStrategy: ModeStrategy get() = strategies[activeMode]!!

    suspend fun switchTo(mode: AppMode) {
        captureEngine.stop()
        strategies[activeMode]?.deactivate()

        _activeModeFlow.value = mode
        hardware.sendCommand(HardwareCommand.SetMode(mode))

        val strategy = strategies[mode] ?: return
        strategy.activate()

        val policy = strategy.capturePolicy
        if (policy is CapturePolicy.Continuous) {
            captureEngine.startContinuous(strategy, policy)
        }
    }
}