package com.feelvision.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val language: Flow<String>
    val speechRate: Flow<Float>
    val hapticEnabled: Flow<Boolean>
    val announceModeSwitch: Flow<Boolean>
    val debugEnabled: Flow<Boolean>

    suspend fun setLanguage(lang: String)
    suspend fun setSpeechRate(rate: Float)
    suspend fun setHaptic(enabled: Boolean)
    suspend fun setAnnounceMode(enabled: Boolean)
    suspend fun setDebug(enabled: Boolean)
}