package com.yaokongqi.remote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class PairRequest(
    val type: String = "pair",
    val magic: Int,
    val pin: String,
    val device: String,
)

@Serializable
data class KeyMessage(
    val type: String = "key",
    val token: String,
    val action: String,
    val vk: Int,
    val mods: Int = 0,
)

@Serializable
data class ComboMessage(
    val type: String = "combo",
    val token: String,
    val vk: Int,
    val mods: Int = 0,
)

@Serializable
data class PingMessage(
    val type: String = "ping",
    val seq: Int = 0,
    val ts: Long = 0L,
)

@Serializable
data class MouseMoveMessage(
    val type: String = "mouse_move",
    val token: String,
    val dx: Int,
    val dy: Int,
)

@Serializable
data class MouseClickMessage(
    val type: String = "mouse_click",
    val token: String,
    val button: String,
    val action: String,
)

@Serializable
data class MouseScrollMessage(
    val type: String = "mouse_scroll",
    val token: String,
    @SerialName("delta_y") val deltaY: Int = 0,
    @SerialName("delta_x") val deltaX: Int = 0,
)

@Serializable
data class TextInputMessage(
    val type: String = "text_input",
    val token: String,
    val text: String,
)

@Serializable
data class SystemMessage(
    val type: String = "system",
    val token: String,
    val action: String,
)

@Serializable
data class IncomingMessage(
    val type: String,
    val token: String? = null,
    @SerialName("pc_name") val pcName: String? = null,
    val code: String? = null,
    val msg: String? = null,
    val seq: Int? = null,
    val ts: Long? = null,
    @SerialName("udp_port") val udpPort: Int? = null,
    @SerialName("udp_key") val udpKey: Long? = null,
)

fun encodePair(magic: Int, pin: String, device: String): String =
    json.encodeToString(PairRequest.serializer(), PairRequest(magic = magic, pin = pin, device = device))

fun encodeKey(token: String, vk: Int, mods: Int, action: String = "tap"): String =
    json.encodeToString(KeyMessage.serializer(), KeyMessage(token = token, action = action, vk = vk, mods = mods))

fun encodeCombo(token: String, vk: Int, mods: Int): String =
    json.encodeToString(ComboMessage.serializer(), ComboMessage(token = token, vk = vk, mods = mods))

fun encodePing(seq: Int = 0, ts: Long = System.currentTimeMillis()): String =
    json.encodeToString(PingMessage.serializer(), PingMessage(seq = seq, ts = ts))

fun encodeMouseMove(token: String, dx: Int, dy: Int): String =
    buildString(48 + token.length) {
        append("{\"type\":\"mouse_move\",\"token\":\"")
        append(token)
        append("\",\"dx\":")
        append(dx)
        append(",\"dy\":")
        append(dy)
        append('}')
    }

fun encodeMouseClick(token: String, button: String, action: String): String =
    json.encodeToString(
        MouseClickMessage.serializer(),
        MouseClickMessage(token = token, button = button, action = action),
    )

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) {
                append("\\u")
                append("%04x".format(ch.code))
            } else {
                append(ch)
            }
        }
    }
    append('"')
}
fun encodeMouseScroll(token: String, deltaY: Int = 0, deltaX: Int = 0): String =
    json.encodeToString(
        MouseScrollMessage.serializer(),
        MouseScrollMessage(token = token, deltaY = deltaY, deltaX = deltaX),
    )

fun encodeTextInput(token: String, text: String): String =
    json.encodeToString(TextInputMessage.serializer(), TextInputMessage(token = token, text = text))

fun encodeSystem(token: String, action: String): String =
    json.encodeToString(SystemMessage.serializer(), SystemMessage(token = token, action = action))

fun encodeInputMode(token: String, mode: String, hz: Int = 180): String =
    buildString(64 + token.length) {
        append("{\"type\":\"input_mode\",\"token\":\"")
        append(token)
        append("\",\"mode\":\"")
        append(mode)
        append("\",\"hz\":")
        append(hz.coerceIn(60, 500))
        append('}')
    }

fun encodeGamepad(
    token: String,
    lx: Int,
    ly: Int,
    rx: Int,
    ry: Int,
    lt: Int = 0,
    rt: Int = 0,
    buttons: Int = 0,
): String = buildString(96 + token.length) {
    append("{\"type\":\"gamepad\",\"token\":\"")
    append(token)
    append("\",\"lx\":").append(lx)
    append(",\"ly\":").append(ly)
    append(",\"rx\":").append(rx)
    append(",\"ry\":").append(ry)
    append(",\"lt\":").append(lt.coerceIn(0, 255))
    append(",\"rt\":").append(rt.coerceIn(0, 255))
    append(",\"buttons\":").append(buttons)
    append('}')
}

fun parseIncoming(raw: String): IncomingMessage =
    json.decodeFromString(IncomingMessage.serializer(), raw)
