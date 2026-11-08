package com.audiokit.catchphrase.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val sensitivity: Int = 70,
    val saveClips: Boolean = true,
    val wakeScreen: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val realtimeAlert: Boolean = true
)

private val Context.settingsDataStore by preferencesDataStore(name = "catchphrase_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val sensitivity = intPreferencesKey("sensitivity")
        val saveClips = booleanPreferencesKey("save_clips")
        val wakeScreen = booleanPreferencesKey("wake_screen")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val realtimeAlert = booleanPreferencesKey("realtime_alert")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            sensitivity = prefs[Keys.sensitivity] ?: 70,
            saveClips = prefs[Keys.saveClips] ?: true,
            wakeScreen = prefs[Keys.wakeScreen] ?: true,
            sound = prefs[Keys.sound] ?: true,
            vibration = prefs[Keys.vibration] ?: true,
            realtimeAlert = prefs[Keys.realtimeAlert] ?: true
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    suspend fun setSensitivity(value: Int) = edit { it[Keys.sensitivity] = value.coerceIn(0, 100) }
    suspend fun setSaveClips(value: Boolean) = edit { it[Keys.saveClips] = value }
    suspend fun setWakeScreen(value: Boolean) = edit { it[Keys.wakeScreen] = value }
    suspend fun setSound(value: Boolean) = edit { it[Keys.sound] = value }
    suspend fun setVibration(value: Boolean) = edit { it[Keys.vibration] = value }
    suspend fun setRealtimeAlert(value: Boolean) = edit { it[Keys.realtimeAlert] = value }
}
