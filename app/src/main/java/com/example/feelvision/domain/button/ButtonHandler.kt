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
import com.feelvision.speech.SpeechRecognitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonHandler @Inject constructor(
    private val coordinator: ModeCoordinator,
    private val captureEngine: CaptureEngine,
    private val tts: TTSManager,
    private val hardware: HardwareSource,
    private val speechRecognizer: SpeechRecognitionManager,
    @ApplicationScope private val scope: CoroutineScope
) {
    init {
        scope.launch {
            hardware.buttonEvents.collect { event -> handle(event) }
        }
        scope.launch {
            speechRecognizer.results.collectLatest { result ->
                handleSpeechResult(result)
            }
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
                        tts.speak("Which mode?")
                        speechRecognizer.startListening()
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

    private fun handleSpeechResult(text: String) {
        val normalized = text.lowercase()
        val targetMode = when {
            normalized.contains("default") || normalized.contains("normal") || normalized.contains("describe") -> AppMode.Default
            normalized.contains("read") || normalized.contains("ocr") || normalized.contains("text") -> AppMode.OCR
            normalized.contains("navigate") || normalized.contains("navigation") || normalized.contains("walk") || normalized.contains("path") -> AppMode.Navigate
            normalized.contains("people") || normalized.contains("face") || normalized.contains("person") || normalized.contains("who") -> AppMode.Face
            normalized.contains("currency") || normalized.contains("money") || normalized.contains("note") || normalized.contains("cash") -> AppMode.Currency
            normalized.contains("education") || normalized.contains("educational") || normalized.contains("learn") || normalized.contains("explain") -> AppMode.Edu
            normalized.contains("narrate") || normalized.contains("narration") || normalized.contains("scene") || normalized.contains("tell") -> AppMode.Narrate
            else -> null
        }

        if (targetMode != null) {
            scope.launch {
                coordinator.switchTo(targetMode)
                tts.speak("Switching to ${targetMode.displayName}")
            }
        } else {
            tts.speak("I didn't understand $text. Try saying a mode name.")
        }
    }
}