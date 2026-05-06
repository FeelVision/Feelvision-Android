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
import com.feelvision.inference.InferenceResult
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
    private val buttonHandler: ButtonHandler
) : ViewModel() {

    private val streamingBuffer = StringBuilder()
    private var pendingImageData: ByteArray? = null

    private val imageProcessingChannel = Channel<ByteArray>(Channel.CONFLATED)

    val serverStatus = luckfoxBridge.status

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

        // Listen for hardware and simulated button events
        viewModelScope.launch {
            merge(hardware.buttonEvents, _buttonEvents).collect { event ->
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
                    processWithGemma(useVoicePrompt = true)
                }
                PhysicalButton.C -> {
                    stopAllTTS()
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

        val image = _currentImage.value
        if (image == null) {
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

            val finalPrompt = if (useVoicePrompt) {
                addLog("Playing beep, waiting for voice prompt...")
                _isListeningForPrompt.value = true
                tts.playBeep()
                val prompt = speechRecognizer.waitForSpeech()
                _isListeningForPrompt.value = false
                prompt ?: "Describe this image in detail."
            } else {
                "Describe this image in detail."
            }

            addLog("Running Gemma inference with prompt: '$finalPrompt'...")

            try {
                val sentenceBuffer = StringBuilder()
                val fullText = StringBuilder()
                val language = try {
                    settings.language.first()
                } catch (e: Exception) {
                    "English"
                }

                gemma.generateStream(
                    prompt = finalPrompt,
                    images = listOf(image),
                    baseSystemPrompt = "You are an assistant. Be concise and precise.",
                    modeTag = "LUCKFOX",
                    responseLanguage = language
                ).collect { result ->
                    when (result) {
                        is InferenceResult.Streaming -> {
                            val chunk = result.partial
                            fullText.append(chunk)
                            sentenceBuffer.append(chunk)
                            _currentResponse.value = fullText.toString()

                            // Flush on sentence boundary
                            val text = sentenceBuffer.toString()
                            val lastBoundary = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                            if (lastBoundary >= 0) {
                                val toSpeak = text.substring(0, lastBoundary + 1).trim()
                                if (toSpeak.isNotEmpty()) {
                                    tts.speakChunk(toSpeak)
                                }
                                sentenceBuffer.clear()
                                sentenceBuffer.append(text.substring(lastBoundary + 1))
                            }
                        }
                        is InferenceResult.Success -> {
                            // Handled by streaming finished
                        }
                        is InferenceResult.Failure -> {
                            addLog("Gemma failed: ${result.error}")
                        }
                        is InferenceResult.NotReady -> {
                            addLog("Model not ready")
                        }
                        is InferenceResult.Cancelled -> {
                            addLog("Inference cancelled")
                        }
                    }
                }

                // Flush remaining buffer
                val remaining = sentenceBuffer.toString().trim()
                if (remaining.isNotEmpty()) {
                    tts.speakChunk(remaining)
                }
                addLog("Gemma Finished.")
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
        luckfoxBridge.stop()
    }
}
