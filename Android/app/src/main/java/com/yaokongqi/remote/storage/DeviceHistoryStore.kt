package com.yaokongqi.remote.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yaokongqi.remote.model.DeviceHistory
import com.yaokongqi.remote.model.SavedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeviceHistoryStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _history = MutableStateFlow(load())
    val history: StateFlow<DeviceHistory> = _history.asStateFlow()

    init {
        migrateLegacySession()
    }

    fun load(): DeviceHistory {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return DeviceHistory()
        return runCatching { json.decodeFromString<DeviceHistory>(raw) }.getOrDefault(DeviceHistory())
    }

    private fun persist(history: DeviceHistory) {
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(history)).commit()
        _history.value = history
    }

    fun upsert(host: String, token: String, pcName: String, port: Int = com.yaokongqi.remote.AppConfig.LISTEN_PORT) {
        val current = load()
        val others = current.devices.filter { it.host != host }
        val updated = DeviceHistory(
            devices = listOf(SavedDevice(host, token, pcName, port)) + others,
            lastHost = host,
        )
        persist(updated)
    }

    fun remove(host: String) {
        val current = load()
        val remaining = current.devices.filter { it.host != host }
        persist(
            DeviceHistory(
                devices = remaining,
                lastHost = if (current.lastHost == host) remaining.firstOrNull()?.host else current.lastHost,
            ),
        )
    }

    fun clearAll() {
        persist(DeviceHistory())
    }

    fun device(host: String): SavedDevice? = load().devices.find { it.host == host }

    fun lastDevice(): SavedDevice? {
        val h = load()
        return h.devices.find { it.host == h.lastHost } ?: h.devices.firstOrNull()
    }

    fun setLastHost(host: String) {
        val current = load()
        if (current.devices.any { it.host == host }) {
            persist(current.copy(lastHost = host))
        }
    }

    fun invalidateToken(host: String) {
        remove(host)
    }

    private fun migrateLegacySession() {
        if (prefs.contains(KEY_HISTORY)) return
        val legacyHost = prefs.getString("host", null) ?: return
        val legacyToken = prefs.getString("token", null) ?: return
        val legacyName = prefs.getString("pc_name", null) ?: legacyHost
        upsert(legacyHost, legacyToken, legacyName)
        prefs.edit()
            .remove("host")
            .remove("token")
            .remove("pc_name")
            .apply()
    }

    companion object {
        private const val PREFS = "yaokongqi_session"
        private const val KEY_HISTORY = "device_history"
    }
}
