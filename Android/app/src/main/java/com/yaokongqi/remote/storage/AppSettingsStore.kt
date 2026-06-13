package com.yaokongqi.remote.storage

import android.content.Context
import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.LayoutMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(raw) }.getOrDefault(AppSettings())
    }

    fun save(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings)).commit()
        _settings.value = settings
    }

    fun updateDraft(settings: AppSettings) {
        _settings.value = settings
    }

    companion object {
        private const val PREFS = "yaokongqi_settings"
        private const val KEY_SETTINGS = "settings"
    }
}
