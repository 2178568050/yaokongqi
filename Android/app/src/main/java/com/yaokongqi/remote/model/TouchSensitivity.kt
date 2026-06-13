package com.yaokongqi.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TouchSensitivity(
    val label: String,
    val moveMultiplier: Float,
    val scrollMultiplier: Float,
) {
    @SerialName("low")
    LOW("低", 0.68f, 0.22f),

    @SerialName("medium")
    MEDIUM("中", 1.15f, 0.35f),

    @SerialName("high")
    HIGH("高", 1.9f, 0.52f),
    ;

    companion object {
        val selectable = entries.toList()
    }
}
