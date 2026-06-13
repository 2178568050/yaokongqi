package com.yaokongqi.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LayoutMode(val label: String) {
    @SerialName("grid_2x2")
    GRID_2X2("2×2"),

    @SerialName("grid_3x3")
    GRID_3X3("3×3"),

    @SerialName("grid_4x2")
    GRID_4X2("4×2"),

    @SerialName("grid_4x4")
    GRID_4X4("4×4"),

    @SerialName("full_keyboard")
    FULL_KEYBOARD("全键"),

    @SerialName("single")
    SINGLE("单按键"),

    @SerialName("dual")
    DUAL("双按键"),
    ;

    val columns: Int
        get() = when (this) {
            GRID_2X2 -> 2
            GRID_3X3 -> 3
            GRID_4X2 -> 4
            GRID_4X4 -> 4
            FULL_KEYBOARD -> 10
            SINGLE -> 1
            DUAL -> 2
        }

    val maxButtons: Int
        get() = when (this) {
            GRID_2X2 -> 4
            GRID_3X3 -> 9
            GRID_4X2 -> 8
            GRID_4X4 -> 16
            FULL_KEYBOARD -> 0
            SINGLE -> 1
            DUAL -> 2
        }

    val rows: Int
        get() = when (this) {
            GRID_2X2 -> 2
            GRID_3X3 -> 3
            GRID_4X2 -> 2
            GRID_4X4 -> 4
            FULL_KEYBOARD -> 0
            SINGLE -> 1
            DUAL -> 1
        }

    val isGrid: Boolean
        get() = this == GRID_2X2 || this == GRID_3X3 || this == GRID_4X2 || this == GRID_4X4

    companion object {
        val selectable = entries.toList()
    }
}
