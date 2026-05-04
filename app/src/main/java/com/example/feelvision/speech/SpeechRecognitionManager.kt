package com.feelvision.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class SpeechPurpose {
    MODE_SWITCH,
    PROMPT
}

@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val speechRecognizer: SpeechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _results = MutableSharedFlow<String>()
    val results: SharedFlow<String> = _results

    private val _promptResults = MutableSharedFlow<String>()
    val promptResults: SharedFlow<String> = _promptResults

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private var currentPurpose: SpeechPurpose = SpeechPurpose.MODE_SWITCH

    private val mainScope = MainScope()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error"
            }
            mainScope.launch { _error.emit(message) }
            Log.e("SpeechRec", "Error: $message ($error)")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                mainScope.launch {
                    if (currentPurpose == SpeechPurpose.PROMPT) {
                        _promptResults.emit(text)
                    } else {
                        _results.emit(text)
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        mainScope.launch {
            speechRecognizer.setRecognitionListener(recognitionListener)
        }
    }

    fun startListening(purpose: SpeechPurpose = SpeechPurpose.MODE_SWITCH) {
        currentPurpose = purpose
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // Request longer listening duration (6 seconds)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        }
        mainScope.launch {
            speechRecognizer.startListening(intent)
        }
    }

    fun stopListening() {
        mainScope.launch {
            speechRecognizer.stopListening()
            _isListening.value = false
        }
    }

    /**
     * Suspending version that waits for a single result.
     * Starts listening, waits for the next emission on 'results', and stops.
     */
    suspend fun waitForSpeech(timeoutMs: Long = 8000L): String? = withContext(Dispatchers.Main) {
        startListening(SpeechPurpose.PROMPT)
        val result = withTimeoutOrNull(timeoutMs) {
            promptResults.first()
        }
        stopListening()
        result
    }

    fun destroy() {
        mainScope.launch {
            speechRecognizer.destroy()
        }
    }
}
