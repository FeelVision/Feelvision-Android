package com.feelvision.domain.capture

import com.feelvision.di.ApplicationScope
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.modes.ModeStrategy
import com.feelvision.hardware.HardwareSource
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureEngine @Inject constructor(
    private val hardware: HardwareSource,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var activeJob: Job? = null
    val isBurstRunning: Boolean get() = activeJob?.isActive == true

    fun execute(strategy: ModeStrategy) {
        when (val policy = strategy.capturePolicy) {
            is CapturePolicy.SingleShot    -> fireSingle(strategy)
            is CapturePolicy.BurstInterval -> fireBurst(strategy, policy)
            is CapturePolicy.Continuous    -> { /* managed by startContinuous */ }
            is CapturePolicy.None          -> { }
        }
    }

    private fun fireSingle(strategy: ModeStrategy) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val bmp = hardware.captureNow() ?: return@launch
            strategy.processFrame(bmp)
        }
    }

    private fun fireBurst(strategy: ModeStrategy, policy: CapturePolicy.BurstInterval) {
        activeJob?.cancel()
        activeJob = scope.launch {
            repeat(policy.count) { i ->
                if (!isActive) return@launch
                val bmp = hardware.captureNow() ?: return@launch
                strategy.processFrame(bmp)
                if (i < policy.count - 1) delay(policy.intervalMs)
            }
        }
    }

    fun startContinuous(strategy: ModeStrategy, policy: CapturePolicy.Continuous) {
        activeJob?.cancel()
        activeJob = scope.launch {
            while (isActive) {
                val bmp = hardware.captureNow()
                if (bmp != null) strategy.processFrame(bmp)
                delay(policy.intervalMs)
            }
        }
    }

    fun cancelBurst() { activeJob?.cancel(); activeJob = null }
    fun stop()        { activeJob?.cancel(); activeJob = null }
}