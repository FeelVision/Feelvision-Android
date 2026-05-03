package com.feelvision.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var lastUtterance = ""
    private var currentLocale = Locale.ENGLISH
    var currentLanguageTag: String = "English"
        private set
    private var speechRate    = 1.0f

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { _isSpeaking.value = true }
                    override fun onDone (id: String?) { _isSpeaking.value = false }
                    override fun onError(id: String?) { _isSpeaking.value = false }
                })
                applySettings()
                Log.d("TTS", "Initialized")
            }
        }
    }

    fun speak(text: String, queue: Boolean = false) {
        lastUtterance = text
        tts?.speak(
            text,
            if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    fun silence()    { tts?.stop(); _isSpeaking.value = false }
    fun repeatLast() { if (lastUtterance.isNotEmpty()) speak(lastUtterance) }

    fun setLanguage(tag: String) {
        currentLanguageTag = tag
        currentLocale = when (tag.lowercase()) {
            "telugu"    -> Locale("te", "IN")
            "hindi"     -> Locale("hi", "IN")
            "tamil"     -> Locale("ta", "IN")
            "kannada"   -> Locale("kn", "IN")
            "malayalam" -> Locale("ml", "IN")
            else        -> Locale.ENGLISH
        }
        applySettings()
    }

    fun setSpeechRate(rate: Float) { speechRate = rate; tts?.setSpeechRate(rate) }

    private fun applySettings() {
        val result = tts?.setLanguage(currentLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.ENGLISH)
        }
        tts?.setSpeechRate(speechRate)
    }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}