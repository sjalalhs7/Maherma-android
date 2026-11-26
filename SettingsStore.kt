package com.maherma.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "maherma_settings")

/** Verify this model id is offered to your key; it is editable in Settings. */
const val DEFAULT_MODEL = "gemini-2.5-flash"

data class AppSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val voiceLocale: String = "",          // BCP-47 tag, "" = system
    val speakReplies: Boolean = true,
    val handsFree: Boolean = false,        // keep listening after each reply
    val voiceBargeIn: Boolean = true,      // only active when the device offers echo cancellation
    val preferOnDevice: Boolean = true,    // on-device recognizer when the device has one (API 33+)
    val memoryEnabled: Boolean = true,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val api = stringPreferencesKey("api_key_enc")
        val model = stringPreferencesKey("model")
        val locale = stringPreferencesKey("voice_locale")
        val speak = booleanPreferencesKey("speak_replies")
        val hands = booleanPreferencesKey("hands_free")
        val barge = booleanPreferencesKey("voice_barge_in")
        val onDevice = booleanPreferencesKey("prefer_on_device")
        val memory = booleanPreferencesKey("memory_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[K.api]?.let { KeyCipher.decrypt(it) }.orEmpty(),
            model = p[K.model] ?: DEFAULT_MODEL,
            voiceLocale = p[K.locale].orEmpty(),
            speakReplies = p[K.speak] ?: true,
            handsFree = p[K.hands] ?: false,
            voiceBargeIn = p[K.barge] ?: true,
            preferOnDevice = p[K.onDevice] ?: true,
            memoryEnabled = p[K.memory] ?: true,
        )
    }

    suspend fun saveBrain(apiKey: String, model: String) = context.dataStore.edit { p ->
        if (apiKey.isBlank()) p.remove(K.api) else p[K.api] = KeyCipher.encrypt(apiKey)
        p[K.model] = model.ifBlank { DEFAULT_MODEL }
    }
    suspend fun setLocale(v: String) = context.dataStore.edit { it[K.locale] = v }
    suspend fun setSpeak(v: Boolean) = context.dataStore.edit { it[K.speak] = v }
    suspend fun setHandsFree(v: Boolean) = context.dataStore.edit { it[K.hands] = v }
    suspend fun setBarge(v: Boolean) = context.dataStore.edit { it[K.barge] = v }
    suspend fun setOnDevice(v: Boolean) = context.dataStore.edit { it[K.onDevice] = v }
    suspend fun setMemory(v: Boolean) = context.dataStore.edit { it[K.memory] = v }
}
