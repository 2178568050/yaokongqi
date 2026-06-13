package com.yaokongqi.remote.protocol

object Modifiers {
    const val SHIFT: UByte = 1u
    const val CTRL: UByte = 2u
    const val ALT: UByte = 4u
    const val WIN: UByte = 8u
}

object Vk {
    const val ENTER: Int = 0x0D
    const val BACKSPACE: Int = 0x08
    const val TAB: Int = 0x09
    const val ESC: Int = 0x1B
    const val SPACE: Int = 0x20
    const val LEFT: Int = 0x25
    const val UP: Int = 0x26
    const val RIGHT: Int = 0x27
    const val DOWN: Int = 0x28
}

fun charToVk(ch: Char): Int? {
    val upper = ch.uppercaseChar()
    if (upper in 'A'..'Z') return upper.code
    if (ch in '0'..'9') return ch.code
    return when (ch) {
        ' ' -> Vk.SPACE
        else -> null
    }
}
