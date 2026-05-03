package com.feelvision.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "feelvision_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object K {
        val LANGUAGE      = stringPreferencesKey ("language")
        val SPEECH_RATE   = floatPreferencesKey  ("speech_rate")
        val HAPTIC        = booleanPreferencesKey("haptic")
        val ANNOUNCE_MODE = booleanPreferencesKey("announce_mode")
        val DEBUG         = booleanPreferencesKey("debug")
    }

    override val language:          Flow<String>  = context.dataStore.data.map { it[K.LANGUAGE]      ?: "English" }
    override val speechRate:        Flow<Float>   = context.dataStore.data.map { it[K.SPEECH_RATE]   ?: 1.0f }
    override val hapticEnabled:     Flow<Boolean> = context.dataStore.data.map { it[K.HAPTIC]        ?: true }
    override val announceModeSwitch:Flow<Boolean> = context.dataStore.data.map { it[K.ANNOUNCE_MODE] ?: true }
    override val debugEnabled:      Flow<Boolean> = context.dataStore.data.map { it[K.DEBUG]         ?: false }

    override suspend fun setLanguage   (lang: String)    { context.dataStore.edit { it[K.LANGUAGE]      = lang } }
    override suspend fun setSpeechRate (rate: Float)     { context.dataStore.edit { it[K.SPEECH_RATE]   = rate } }
    override suspend fun setHaptic     (enabled: Boolean){ context.dataStore.edit { it[K.HAPTIC]        = enabled } }
    override suspend fun setAnnounceMode(enabled: Boolean){ context.dataStore.edit { it[K.ANNOUNCE_MODE]= enabled } }
    override suspend fun setDebug      (enabled: Boolean){ context.dataStore.edit { it[K.DEBUG]         = enabled } }
}