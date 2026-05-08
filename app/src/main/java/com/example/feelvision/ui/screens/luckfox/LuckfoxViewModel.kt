package com.feelvision.ui.screens.luckfox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.domain.button.ButtonHandler
import com.feelvision.domain.model.ButtonEvent
import com.feelvision.domain.model.PhysicalButton
import com.feelvision.hardware.HardwareSource
import com.feelvision.hardware.LuckfoxBridge
import com.feelvision.inference.GemmaInferenceManager
import com.feelvision.luckfox.LuckfoxTcpServer
import com.feelvision.speech.SpeechRecognitionManager
import com.feelvision.tts.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

import com.feelvision.domain.coordinator.ModeCoordinator
import com.feelvision.domain.model.AppMode
import com.feelvision.inference.ModePrompts

enum class LuckfoxTtsState {
    IDLE,
    PLAYING
}

@HiltViewModel
class LuckfoxViewModel @Inject constructor(
    private val gemma: GemmaInferenceManager,
    private val tts: TTSManager,
    private val luckfoxBridge: LuckfoxBridge,
    private val speechRecognizer: SpeechRecognitionManager,
    private val settings: SettingsRepository,
    private val hardware: HardwareSource,
    private val buttonHandler: ButtonHandler,
    private val coordinator: ModeCoordinator
) : ViewModel() {

    private val streamingBuffer = StringBuilder()
    private var pendingImageData: ByteArray? = null

    private val imageProcessingChannel = Channel<ByteArray>(Channel.CONFLATED)

    val serverStatus = luckfoxBridge.status

    val currentMode: StateFlow<AppMode> = coordinator.activeModeFlow

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentImage = MutableStateFlow<Bitmap?>(null)
    val currentImage: StateFlow<Bitmap?> = _currentImage.asStateFlow()

    private val _imageTimestamp = MutableStateFlow<String?>(null)
    val imageTimestamp: StateFlow<String?> = _imageTimestamp.asStateFlow()

    private val _imageSize = MutableStateFlow(0)
    val imageSize: StateFlow<Int> = _imageSize.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = tts.isSpeaking

    val ttsState: StateFlow<LuckfoxTtsState> = tts.isSpeaking.map { speaking ->
        if (speaking) LuckfoxTtsState.PLAYING else LuckfoxTtsState.IDLE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LuckfoxTtsState.IDLE)

    private val _isListeningForPrompt = MutableStateFlow(false)
    val isListeningForPrompt: StateFlow<Boolean> = _isListeningForPrompt.asStateFlow()

    private val _gemmaReady = MutableStateFlow(false)
    val gemmaReady: StateFlow<Boolean> = _gemmaReady.asStateFlow()

    private val _buttonEvents = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 16)

    private var activeJob: Job? = null

    private fun cancelActiveCapture() {
        activeJob?.let {
            if (it.isActive) {
                gemma.cancelInference()
                speechRecognizer.stopListening()
                tts.silence()
                it.cancel()
            }
        }
    }

    init {
        // Mark Luckfox screen as active for physical button redirection
        buttonHandler.isLuckfoxActive = true

        // Collect server logs
        viewModelScope.launch {
            if (luckfoxBridge is LuckfoxTcpServer) {
                luckfoxBridge.logs.collect { msg ->
                    addLog(msg)
                }
            }
        }

        // Collect received images
        viewModelScope.launch {
            luckfoxBridge.receivedImages.collect { jpegData ->
                handleImageReceived(jpegData)
            }
        }

        // Sync model ready state
        viewModelScope.launch {
            while (true) {
                _gemmaReady.value = gemma.isReady()
                delay(1000)
            }
        }

        // Listen for hardware, luckfox glasses, and simulated button events
        viewModelScope.launch {
            merge(hardware.buttonEvents, _buttonEvents, luckfoxBridge.buttonEvents).collect { event ->
                handleButtonEvent(event)
            }
        }

        startImageProcessingWorker()
    }

    private fun startImageProcessingWorker() {
        viewModelScope.launch(Dispatchers.Default) {
            for (jpegData in imageProcessingChannel) {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            _currentImage.value = bitmap
                            _imageTimestamp.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        }
                    }
                } catch (e: Exception) {
                    addLog("Decode error: ${e.message}")
                }
            }
        }
    }

    fun startServer() {
        luckfoxBridge.start()
    }

    fun stopServer() {
        luckfoxBridge.stop()
    }

    private fun handleImageReceived(jpegData: ByteArray) {
        pendingImageData = jpegData
        _imageSize.value = jpegData.size
        imageProcessingChannel.trySend(jpegData)
    }

    fun simulateButton(button: PhysicalButton, type: String = "short") {
        val event = when (type) {
            "long"   -> ButtonEvent.LongPress(button)
            "double" -> ButtonEvent.DoubleTap(button)
            "hold5"  -> ButtonEvent.Hold5s(button)
            else     -> ButtonEvent.ShortPress(button)
        }
        viewModelScope.launch {
            _buttonEvents.emit(event)
        }
    }

    private fun handleButtonEvent(event: ButtonEvent) {
        val buttonName = when (event) {
            is ButtonEvent.ShortPress -> "ShortPress(${event.button})"
            is ButtonEvent.LongPress -> "LongPress(${event.button})"
            is ButtonEvent.DoubleTap -> "DoubleTap(${event.button})"
            is ButtonEvent.Hold5s -> "Hold5s(${event.button})"
        }
        addLog("[BTN] Triggered $buttonName")

        when (event) {
            is ButtonEvent.ShortPress -> when (event.button) {
                PhysicalButton.A -> {
                    processWithGemma(useVoicePrompt = false)
                }
                PhysicalButton.B -> {
                    viewModelScope.launch {
                        val nextMode = AppMode.next(coordinator.activeMode)
                        coordinator.switchTo(nextMode)
                        tts.speak(nextMode.getLocalizedAnnouncement(tts.currentLanguageTag))
                        addLog("Switched to mode: ${nextMode.displayName}")
                    }
                }
                PhysicalButton.C -> {
                    stopAllTTS()
                }
            }
            is ButtonEvent.LongPress -> when (event.button) {
                PhysicalButton.A -> {
                    processWithGemma(useVoicePrompt = true)
                }
                PhysicalButton.B -> {
                    viewModelScope.launch {
                        tts.speak("Which mode?")
                        speechRecognizer.startListening()
                        addLog("Started Voice Mode Switcher")
                    }
                }
                PhysicalButton.C -> {
                    playTTS()
                }
            }
            is ButtonEvent.DoubleTap -> when (event.button) {
                PhysicalButton.C -> {
                    playTTS()
                }
                else -> Unit
            }
            else -> Unit
        }
    }

    fun processWithGemma(useVoicePrompt: Boolean) {
        cancelActiveCapture()

        val originalBmp = _currentImage.value
        if (originalBmp == null) {
            addLog("No image to process")
            return
        }

        activeJob = viewModelScope.launch {
            if (!gemma.isReady()) {
                addLog("Model not ready yet")
                tts.speak("Model not ready yet.")
                return@launch
            }

            _isProcessing.value = true
            _currentResponse.value = ""
            streamingBuffer.clear()

            val voicePrompt = if (useVoicePrompt) {
                addLog("Playing beep, waiting for voice prompt...")
                _isListeningForPrompt.value = true
                tts.playBeep()
                val prompt = speechRecognizer.waitForSpeech()
                _isListeningForPrompt.value = false
                if (prompt.isNullOrBlank()) null else prompt
            } else {
                null
            }

            val finalPrompt = voicePrompt ?: when (coordinator.activeMode) {
                AppMode.Default -> ModePrompts.DEFAULT
                AppMode.OCR -> ModePrompts.OCR
                AppMode.Navigate -> ModePrompts.NAVIGATE
                AppMode.Face -> ModePrompts.FACE
                AppMode.Currency -> ModePrompts.CURRENCY
                AppMode.Edu -> ModePrompts.EDU
                AppMode.Narrate -> ModePrompts.NARRATE
            }

            val strategy = coordinator.activeStrategy
            addLog("Running active strategy: ${strategy.mode.displayName} with prompt: '${finalPrompt}'...")

            try {
                // Create a copy because the strategy recycles the passed bitmap in its finally block
                val processingBmp = originalBmp.copy(originalBmp.config ?: Bitmap.Config.ARGB_8888, true)
                
                val result = strategy.processFrameStreaming(processingBmp, finalPrompt) { chunk ->
                    withContext(Dispatchers.Main) {
                        streamingBuffer.append(chunk).append(" ")
                        _currentResponse.value = streamingBuffer.toString().trim()
                    }
                }
                addLog("Gemma Finished with result: $result")
            } catch (e: CancellationException) {
                addLog("Inference cancelled")
                throw e
            } catch (e: Exception) {
                addLog("Processing error: ${e.message}")
            } finally {
                _isProcessing.value = false
                _isListeningForPrompt.value = false
            }
        }
    }

    // Overload for simple UI action triggers
    fun processWithGemma() {
        processWithGemma(useVoicePrompt = true)
    }

    fun playTTS() {
        val text = _currentResponse.value
        if (text.isNotEmpty()) {
            tts.speak(text)
        }
    }

    fun stopAllTTS() {
        tts.silence()
    }

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formattedMsg = "[$time] $msg"
        Log.d("LuckfoxVM", formattedMsg)
        _logMessages.value = _logMessages.value + formattedMsg
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveCapture()
        buttonHandler.isLuckfoxActive = false
    }
}
