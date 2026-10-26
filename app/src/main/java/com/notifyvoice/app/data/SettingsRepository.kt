package com.notifyvoice.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.notifyvoice.app.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("notifyvoice_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

class SettingsRepository(context: Context, scope: CoroutineScope, private val onError: () -> Unit) {
    private val store = context.settingsStore
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    val settings = store.data.catch { error ->
        if (error !is IOException) throw error
        onError()
        emit(emptyPreferences())
    }.map { p ->
        Settings(
            enabled = p[booleanPreferencesKey("enabled")] ?: false,
            speed = (p[floatPreferencesKey("speed")] ?: 1f).coerceIn(.6f, 1.6f),
            volume = (p[floatPreferencesKey("volume")] ?: .7f).coerceIn(0f, 1f),
            voiceName = p[stringPreferencesKey("voice")] ?: "",
            approvedDevices = p[stringSetPreferencesKey("approved_devices")] ?: emptySet(),
            apps = ChatApp.entries.associateWith { app ->
                AppRule(p[booleanPreferencesKey("${app.name}_enabled")] ?: true,
                    ReadingMode.entries.firstOrNull { it.name == p[stringPreferencesKey("${app.name}_mode")] }
                        ?: ReadingMode.SENDER,
                    p[booleanPreferencesKey("${app.name}_groups")] ?: false)
            },
            excludedConversations = decode(p[stringPreferencesKey("excluded_chats")]),
            excludedSenders = decode(p[stringPreferencesKey("excluded_senders")]),
        )
    }.onEach { _loaded.value = true }.stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun setEnabled(enabled: Boolean) = save { it[booleanPreferencesKey("enabled")] = enabled }
    suspend fun setSpeed(speed: Float) = save { it[floatPreferencesKey("speed")] = speed.coerceIn(.6f, 1.6f) }
    suspend fun setVolume(volume: Float) = save { it[floatPreferencesKey("volume")] = volume.coerceIn(0f, 1f) }
    suspend fun setVoice(name: String) = save { it[stringPreferencesKey("voice")] = name }
    suspend fun approveDevice(id: String, approve: Boolean) = save { p ->
        val key = stringSetPreferencesKey("approved_devices")
        p[key] = if (approve) p[key].orEmpty() + id else p[key].orEmpty() - id
    }
    suspend fun setRule(app: ChatApp, rule: AppRule) = save {
        it[booleanPreferencesKey("${app.name}_enabled")] = rule.enabled
        it[stringPreferencesKey("${app.name}_mode")] = rule.mode.name
        it[booleanPreferencesKey("${app.name}_groups")] = rule.skipGroups
    }
    suspend fun excludeConversation(id: String, label: String, exclude: Boolean) = exclude("excluded_chats", id, label, exclude)
    suspend fun excludeSender(id: String, label: String, exclude: Boolean) = exclude("excluded_senders", id, label, exclude)

    private suspend fun exclude(key: String, id: String, label: String, exclude: Boolean) = save { p ->
        val map = decode(p[stringPreferencesKey(key)]).toMutableMap()
        if (exclude) map[id] = label.take(160) else map.remove(id)
        p[stringPreferencesKey(key)] = JSONObject(map as Map<*, *>).toString()
    }
    private suspend fun save(block: (MutablePreferences) -> Unit) {
        try { store.edit(block) } catch (_: IOException) { onError() }
    }
    private fun decode(value: String?): Map<String, String> = runCatching {
        val json = JSONObject(value ?: "{}")
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())
}
