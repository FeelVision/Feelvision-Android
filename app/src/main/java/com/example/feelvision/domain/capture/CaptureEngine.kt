package com.feelvision.domain.capture

import com.feelvision.di.ApplicationScope
import com.feelvision.domain.model.CapturePolicy
import com.feelvision.domain.modes.ModeStrategy
import com.feelvision.hardware.HardwareSource
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.tts.TTSManager
import com.feelvision.speech.SpeechRecognitionManager
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureEngine @Inject constructor(
    private val hardware: HardwareSource,
    private val gemma: GemmaInferenceManager,
    private val tts: TTSManager,
    private val speechRecognizer: SpeechRecognitionManager,
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

    /**
     * Cancel the previous job by first stopping native inference via
     * conversation.cancelProcess(), then cancelling the coroutine.
     */
    private fun cancelActive() {
        activeJob?.let {
            if (it.isActive) {
                gemma.cancelInference()   // tells native layer to stop NOW
                speechRecognizer.stopListening()
                tts.silence()
                it.cancel()               // cancel the coroutine
            }
        }
    }

    private fun fireSingle(strategy: ModeStrategy) {
        cancelActive()
        activeJob = scope.launch {
            val bmp = hardware.captureNow() ?: return@launch
            tts.playBeep()
            val prompt = speechRecognizer.waitForSpeech()
            strategy.processFrameStreaming(bmp, prompt) { /* TTS handled inside strategy */ }
        }
    }

    private fun fireBurst(strategy: ModeStrategy, policy: CapturePolicy.BurstInterval) {
        cancelActive()
        activeJob = scope.launch {
            val frames = mutableListOf<android.graphics.Bitmap>()
            repeat(policy.count) { i ->
                if (!isActive) {
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    return@launch
                }
                val bmp = hardware.captureNow()
                if (bmp == null) {
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    return@launch
                }
                frames.add(bmp)
                if (i < policy.count - 1) delay(policy.intervalMs)
            }
            if (frames.isNotEmpty()) {
                tts.playBeep()
                val prompt = speechRecognizer.waitForSpeech()
                strategy.processFramesStreaming(frames, prompt) { /* TTS handled inside strategy */ }
            }
        }
    }

    fun startContinuous(strategy: ModeStrategy, policy: CapturePolicy.Continuous) {
        cancelActive()
        activeJob = scope.launch {
            while (isActive) {
                val bmp = hardware.captureNow()
                if (bmp != null) strategy.processFrameStreaming(bmp) { /* TTS handled inside strategy */ }
                delay(policy.intervalMs)
            }
        }
    }

    fun cancelBurst() { cancelActive(); activeJob = null }
    fun stop()        { cancelActive(); activeJob = null }
}