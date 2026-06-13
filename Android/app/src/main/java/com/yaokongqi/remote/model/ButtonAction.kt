package com.yaokongqi.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ButtonAction {
    @SerialName("key")
    KEY,

    @SerialName("volume_up")
    VOLUME_UP,

    @SerialName("volume_down")
    VOLUME_DOWN,

    @SerialName("shutdown")
    SHUTDOWN,

    @SerialName("open_keyboard")
    OPEN_KEYBOARD,
}
