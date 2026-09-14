package com.everywhen.offlinemusic

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("player_preferences")

class PlayerPreferences(private val context: Context) {
    private val speedKey = floatPreferencesKey("playback_speed")
    private val lastScreenKey = stringPreferencesKey("last_screen")

    val speed: Flow<Float> = context.dataStore.data.map { it[speedKey] ?: 1f }
    val lastScreen: Flow<String> = context.dataStore.data.map { it[lastScreenKey] ?: "tracks" }

    suspend fun setSpeed(speed: Float) {
        context.dataStore.edit { it[speedKey] = speed }
    }

    suspend fun setLastScreen(screen: String) {
        context.dataStore.edit { it[lastScreenKey] = screen }
    }
}
