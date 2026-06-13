package com.yaokongqi.remote.storage

import android.content.Context
import com.yaokongqi.remote.model.ButtonLayout
import com.yaokongqi.remote.model.GridLayoutHelper
import com.yaokongqi.remote.model.LayoutMode
import com.yaokongqi.remote.model.RemoteButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ButtonLayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<ButtonLayout> = _layout.asStateFlow()

    fun load(): ButtonLayout {
        val raw = prefs.getString(KEY_LAYOUT, null) ?: return ButtonLayout()
        val decoded = runCatching { json.decodeFromString<ButtonLayout>(raw) }.getOrDefault(ButtonLayout())
        return normalize(decoded)
    }

    fun save(layout: ButtonLayout) {
        val normalized = normalize(layout)
        prefs.edit().putString(KEY_LAYOUT, json.encodeToString(normalized)).apply()
        _layout.value = normalized
    }

    fun setLayoutMode(mode: LayoutMode) {
        val current = _layout.value
        val trimmed = trimButtons(current.buttons, mode.maxButtons)
        save(
            current.copy(
                layoutMode = mode,
                columns = mode.columns,
                buttons = trimmed,
            ),
        )
    }

    fun addButton(button: RemoteButton, layoutMode: LayoutMode? = null): Boolean {
        val current = _layout.value
        val mode = layoutMode ?: current.layoutMode
        if (mode == LayoutMode.FULL_KEYBOARD) return false
        val activeButtons = current.buttons.take(mode.maxButtons)
        if (activeButtons.size >= mode.maxButtons) return false
        val toAdd = if (mode.isGrid) {
            val columns = mode.columns
            val rows = mode.rows
            val prepared = GridLayoutHelper.prepareForGrid(button, columns, rows)
            if (!GridLayoutHelper.canAddButton(activeButtons, prepared, columns, rows)) return false
            prepared
        } else {
            button
        }
        save(current.copy(buttons = current.buttons + toAdd))
        return true
    }

    fun updateButton(button: RemoteButton) {
        val current = _layout.value
        save(
            current.copy(
                buttons = current.buttons.map { if (it.id == button.id) button else it },
            ),
        )
    }

    fun removeButton(id: String) {
        val current = _layout.value
        save(current.copy(buttons = current.buttons.filter { it.id != id }))
    }

    fun resetDefault() {
        save(ButtonLayout())
    }

    private fun normalize(layout: ButtonLayout): ButtonLayout {
        val mode = layout.layoutMode
        val columns = mode.columns
        val rows = mode.rows
        val buttons = trimButtons(layout.buttons, mode.maxButtons).map { button ->
            if (mode.isGrid) GridLayoutHelper.normalizeButton(button, columns, rows) else button
        }
        return layout.copy(layoutMode = mode, columns = columns, buttons = buttons)
    }

    private fun trimButtons(buttons: List<RemoteButton>, max: Int): List<RemoteButton> =
        if (max == 0) emptyList() else buttons.take(max)

    companion object {
        private const val PREFS = "yaokongqi_layout"
        private const val KEY_LAYOUT = "layout"
    }
}
