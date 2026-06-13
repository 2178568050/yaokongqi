package com.yaokongqi.remote.storage

import android.content.Context
import com.yaokongqi.remote.model.ButtonLayout
import com.yaokongqi.remote.model.LayoutPreset
import com.yaokongqi.remote.model.LayoutPresetCollection
import com.yaokongqi.remote.model.ScenarioLayoutPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LayoutPresetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _collection = MutableStateFlow(load())
    val collection: StateFlow<LayoutPresetCollection> = _collection.asStateFlow()

    fun load(): LayoutPresetCollection {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return LayoutPresetCollection.default()
        return runCatching { json.decodeFromString<LayoutPresetCollection>(raw) }
            .getOrDefault(LayoutPresetCollection.default())
            .let { normalize(it) }
    }

    fun save(collection: LayoutPresetCollection) {
        val normalized = normalize(collection)
        prefs.edit().putString(KEY_PRESETS, json.encodeToString(normalized)).apply()
        _collection.value = normalized
    }

    fun saveCurrentLayout(name: String, layout: ButtonLayout) {
        val current = _collection.value
        val preset = LayoutPreset(name = name, layout = layout)
        save(
            current.copy(
                presets = current.presets + preset,
                activeIndex = current.presets.size,
            ),
        )
    }

    fun updateActiveLayout(layout: ButtonLayout) {
        val current = _collection.value
        if (current.presets.isEmpty()) return
        val index = current.activeIndex.coerceIn(0, current.presets.lastIndex)
        if (ScenarioLayoutPresets.isBuiltin(current.presets[index].id)) return
        val updated = current.presets.toMutableList()
        updated[index] = updated[index].copy(layout = layout)
        save(current.copy(presets = updated))
    }

    fun switchTo(index: Int): LayoutPreset? {
        val current = _collection.value
        if (index !in current.presets.indices) return null
        save(current.copy(activeIndex = index))
        return current.presets[index]
    }

    fun switchNext(): LayoutPreset? {
        val current = _collection.value
        if (current.presets.isEmpty()) return null
        val next = (current.activeIndex + 1) % current.presets.size
        return switchTo(next)
    }

    fun switchPrevious(): LayoutPreset? {
        val current = _collection.value
        if (current.presets.isEmpty()) return null
        val prev = if (current.activeIndex == 0) current.presets.lastIndex else current.activeIndex - 1
        return switchTo(prev)
    }

    fun renamePreset(id: String, name: String) {
        val current = _collection.value
        save(
            current.copy(
                presets = current.presets.map { if (it.id == id) it.copy(name = name) else it },
            ),
        )
    }

    fun deletePreset(id: String) {
        val current = _collection.value
        val index = current.presets.indexOfFirst { it.id == id }
        if (index < 0) return
        val remaining = current.presets.filter { it.id != id }
        if (remaining.isEmpty()) {
            save(LayoutPresetCollection.default())
            return
        }
        val newIndex = when {
            index < current.activeIndex -> current.activeIndex - 1
            index == current.activeIndex -> current.activeIndex.coerceAtMost(remaining.lastIndex)
            else -> current.activeIndex
        }
        save(current.copy(presets = remaining, activeIndex = newIndex))
    }

    fun ensureBuiltinPresets() {
        val builtins = ScenarioLayoutPresets.all()
        val canonicalById = builtins.associateBy { it.id }
        val current = _collection.value
        if (current.presets.isEmpty()) {
            save(LayoutPresetCollection(presets = builtins, activeIndex = 0))
            return
        }
        val existingIds = current.presets.map { it.id }.toSet()
        val missing = builtins.filter { it.id !in existingIds }
        val repaired = (current.presets + missing).map { preset ->
            canonicalById[preset.id]?.let { canonical ->
                preset.copy(layout = canonical.layout)
            } ?: preset
        }
        if (missing.isNotEmpty() || repaired != current.presets) {
            save(current.copy(presets = repaired))
        }
    }

    fun ensureDefaultPreset(layout: ButtonLayout) {
        ensureBuiltinPresets()
        val current = _collection.value
        if (current.presets.isNotEmpty()) return
        save(
            LayoutPresetCollection(
                presets = listOf(LayoutPreset(id = UUID.randomUUID().toString(), name = "默认", layout = layout)),
                activeIndex = 0,
            ),
        )
    }

    private fun normalize(collection: LayoutPresetCollection): LayoutPresetCollection {
        if (collection.presets.isEmpty()) return LayoutPresetCollection.default()
        val index = collection.activeIndex.coerceIn(0, collection.presets.lastIndex)
        return collection.copy(activeIndex = index)
    }

    companion object {
        private const val PREFS = "yaokongqi_presets"
        private const val KEY_PRESETS = "presets"
    }
}
