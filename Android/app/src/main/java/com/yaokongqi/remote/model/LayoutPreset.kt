package com.yaokongqi.remote.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LayoutPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val layout: ButtonLayout,
)

@Serializable
data class LayoutPresetCollection(
    val presets: List<LayoutPreset> = emptyList(),
    val activeIndex: Int = 0,
) {
    fun activePreset(): LayoutPreset? = presets.getOrNull(activeIndex.coerceIn(0, (presets.size - 1).coerceAtLeast(0)))

    companion object {
        fun default(): LayoutPresetCollection = LayoutPresetCollection(
            presets = ScenarioLayoutPresets.all(),
            activeIndex = 0,
        )
    }
}
