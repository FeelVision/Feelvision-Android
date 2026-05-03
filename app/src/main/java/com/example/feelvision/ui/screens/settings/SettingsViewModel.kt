package com.feelvision.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.tts.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val language: String = "English",
    val speechRate: Float = 1.0f,
    val hapticEnabled: Boolean = true,
    val announceModeSwitch: Boolean = true,
    val debugEnabled: Boolean = false,
    val deviceName: String = "",
    val devicePaired: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val tts: TTSManager
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        repo.language, repo.speechRate, repo.hapticEnabled,
        repo.announceModeSwitch, repo.debugEnabled
    ) { lang, rate, haptic, announce, debug ->
        SettingsUiState(
            language = lang,
            speechRate = rate,
            hapticEnabled = haptic,
            announceModeSwitch = announce,
            debugEnabled = debug,
            deviceName = "",
            devicePaired = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setLanguage(lang: String) = viewModelScope.launch {
        repo.setLanguage(lang); tts.setLanguage(lang)
    }
    fun setSpeechRate(rate: Float) = viewModelScope.launch {
        repo.setSpeechRate(rate); tts.setSpeechRate(rate)
    }
    fun setHaptic(v: Boolean) = viewModelScope.launch { repo.setHaptic(v) }
    fun setAnnounceMode(v: Boolean) = viewModelScope.launch { repo.setAnnounceMode(v) }
    fun setDebug(v: Boolean) = viewModelScope.launch { repo.setDebug(v) }
    
    fun openPairing() {
        // To be implemented
    }
}