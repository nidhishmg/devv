package com.devaki.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devaki_settings")

class SettingsStore(private val context: Context) {
    
    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_BASE_SPEED = intPreferencesKey("base_speed")
        private val KEY_FOLLOW_ENABLED = booleanPreferencesKey("follow_enabled")
        private val KEY_SIMULATOR = booleanPreferencesKey("simulator")
        private val KEY_LLM_URL = stringPreferencesKey("llm_url")
        
        const val DEFAULT_HOST = "192.168.1.100"
        const val DEFAULT_PORT = 9000
        const val DEFAULT_BASE_SPEED = 180
        const val DEFAULT_LLM_URL = "http://localhost:11434"
    }
    
    // Host
    val host: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOST] ?: DEFAULT_HOST
    }
    
    suspend fun setHost(value: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = value
        }
    }
    
    // Port
    val port: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PORT] ?: DEFAULT_PORT
    }
    
    suspend fun setPort(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PORT] = value
        }
    }
    
    // Base Speed
    val baseSpeed: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_SPEED] ?: DEFAULT_BASE_SPEED
    }
    
    suspend fun setBaseSpeed(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_SPEED] = value.coerceIn(0, 255)
        }
    }
    
    // Follow Mode
    val followEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FOLLOW_ENABLED] ?: false
    }
    
    suspend fun setFollowEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOLLOW_ENABLED] = value
        }
    }
    
    // Wi-Fi Simulator
    val simulator: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SIMULATOR] ?: false
    }
    
    suspend fun setSimulator(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SIMULATOR] = value
        }
    }
    
    // LLM URL
    val llmUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LLM_URL] ?: DEFAULT_LLM_URL
    }
    
    suspend fun setLlmUrl(value: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LLM_URL] = value
        }
    }
}
