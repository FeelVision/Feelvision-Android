package com.feelvision.domain.button

import com.feelvision.di.ApplicationScope
import com.feelvision.domain.capture.CaptureEngine
import com.feelvision.domain.coordinator.ModeCoordinator
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.ButtonEvent
import com.feelvision.domain.model.PhysicalButton
import com.feelvision.hardware.HardwareSource
import com.feelvision.hardware.PhoneCameraSource
import com.feelvision.tts.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonHandler @Inject constructor(
    private val coordinator: ModeCoordinator,
    private val captureEngine: CaptureEngine,
    private val tts: TTSManager,
    private val hardware: HardwareSource,
    @ApplicationScope private val scope: CoroutineScope
) {
    init {
        scope.launch {
            hardware.buttonEvents.collect { event -> handle(event) }
        }
    }

    private fun handle(event: ButtonEvent) {
        scope.launch {
            when (event) {
                is ButtonEvent.ShortPress -> when (event.button) {
                    PhysicalButton.A -> {
                        if (captureEngine.isBurstRunning) {
                            captureEngine.cancelBurst()
                            tts.speak("Cancelled.")
                        } else {
                            captureEngine.execute(coordinator.activeStrategy)
                        }
                    }
                    PhysicalButton.B -> {
                        val next = AppMode.next(coordinator.activeMode)
                        coordinator.switchTo(next)
                        tts.speak(next.getLocalizedAnnouncement(tts.currentLanguageTag))
                    }
                    PhysicalButton.C -> tts.silence()
                }
                is ButtonEvent.LongPress -> when (event.button) {
                    PhysicalButton.B -> {
                        val prev = AppMode.prev(coordinator.activeMode)
                        coordinator.switchTo(prev)
                        tts.speak(prev.getLocalizedAnnouncement(tts.currentLanguageTag))
                    }
                    else -> Unit
                }
                is ButtonEvent.DoubleTap -> when (event.button) {
                    PhysicalButton.C -> tts.repeatLast()
                    else -> Unit
                }
                else -> Unit
            }
        }
    }

    fun simulatePress(button: PhysicalButton, type: String = "short") {
        (hardware as? PhoneCameraSource)?.simulateButtonPress(button, type)
    }
}