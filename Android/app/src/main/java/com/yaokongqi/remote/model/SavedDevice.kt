package com.yaokongqi.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedDevice(
    val host: String,
    val token: String,
    val pcName: String,
    val port: Int = 10825,
    val savedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DeviceHistory(
    val devices: List<SavedDevice> = emptyList(),
    val lastHost: String? = null,
)
