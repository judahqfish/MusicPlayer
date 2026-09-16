package com.everywhen.offlinemusic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("player_preferences")

class PlayerPreferences(private val context: Context) {
    private val speedKey = floatPreferencesKey("playback_speed")
    private val lastScreenKey = stringPreferencesKey("last_screen")
    private val amplifierDbKey = floatPreferencesKey("amplifier_db")
    private val visualizerScreensaverKey = booleanPreferencesKey("visualizer_screensaver")
    private val visualizerStyleKey = stringPreferencesKey("visualizer_style")

    val speed: Flow<Float> = context.dataStore.data.map { it[speedKey] ?: 1f }
    val lastScreen: Flow<String> = context.dataStore.data.map { it[lastScreenKey] ?: "tracks" }
    val amplifierDb: Flow<Float> = context.dataStore.data.map { (it[amplifierDbKey] ?: 0f).coerceIn(0f, 12f) }
    val visualizerScreensaver: Flow<Boolean> = context.dataStore.data.map { it[visualizerScreensaverKey] ?: false }
    val visualizerStyle: Flow<String> = context.dataStore.data.map { it[visualizerStyleKey] ?: "Kaleidoscope" }

    suspend fun setSpeed(speed: Float) {
        context.dataStore.edit { it[speedKey] = speed }
    }

    suspend fun setLastScreen(screen: String) {
        context.dataStore.edit { it[lastScreenKey] = screen }
    }

    suspend fun setAmplifierDb(db: Float) {
        context.dataStore.edit { it[amplifierDbKey] = db.coerceIn(0f, 12f) }
    }

    suspend fun setVisualizerScreensaver(enabled: Boolean) {
        context.dataStore.edit { it[visualizerScreensaverKey] = enabled }
    }

    suspend fun setVisualizerStyle(style: String) {
        val safe = style.takeIf { it in setOf("Radial", "Kaleidoscope", "Blob", "Drops", "Nebula") } ?: "Kaleidoscope"
        context.dataStore.edit { it[visualizerStyleKey] = safe }
    }
}
