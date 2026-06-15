package com.yaokongqi.remote.model

/** XInput 按键位（与 PC ViGEm 一致） */
object GamepadButtons {
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000
    const val LB = 0x0100
    const val RB = 0x0200
    const val BACK = 0x0020
    const val START = 0x0010
    const val LS = 0x0040
    const val RS = 0x0080
    const val UP = 0x0001
    const val DOWN = 0x0002
    const val LEFT = 0x0004
    const val RIGHT = 0x0008
}

enum class RemoteInputMode {
    KEYBOARD_MOUSE,
    GAMEPAD,
}

data class GamepadSnapshot(
    val lx: Int = 0,
    val ly: Int = 0,
    val rx: Int = 0,
    val ry: Int = 0,
    val lt: Int = 0,
    val rt: Int = 0,
    val buttons: Int = 0,
)
