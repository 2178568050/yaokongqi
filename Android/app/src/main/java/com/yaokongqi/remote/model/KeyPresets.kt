package com.yaokongqi.remote.model

data class KeyPreset(
    val label: String,
    val vk: Int = 0,
    val mods: Int = 0,
    val action: ButtonAction = ButtonAction.KEY,
)

object KeyPresets {
    private fun letters(): List<KeyPreset> =
        ('A'..'Z').map { KeyPreset(it.toString(), 0x41 + (it - 'A')) }

    private fun digits(): List<KeyPreset> =
        ('0'..'9').map { KeyPreset(it.toString(), it.code) }

    private fun functionKeys(): List<KeyPreset> =
        (1..12).map { KeyPreset("F$it", 0x6F + it) }

    private fun symbols(): List<KeyPreset> = listOf(
        KeyPreset("` ~", 0xC0),
        KeyPreset("- _", 0xBD),
        KeyPreset("= +", 0xBB),
        KeyPreset("[ {", 0xDB),
        KeyPreset("] }", 0xDD),
        KeyPreset("\\ |", 0xDC),
        KeyPreset("; :", 0xBA),
        KeyPreset("' \"", 0xDE),
        KeyPreset(", <", 0xBC),
        KeyPreset(". >", 0xBE),
        KeyPreset("/ ?", 0xBF),
    )

    private fun navigation(): List<KeyPreset> = listOf(
        KeyPreset("Home", 0x24),
        KeyPreset("End", 0x23),
        KeyPreset("PageUp", 0x21),
        KeyPreset("PageDown", 0x22),
        KeyPreset("Insert", 0x2D),
        KeyPreset("Delete", 0x2E),
        KeyPreset("↑", 0x26),
        KeyPreset("↓", 0x28),
        KeyPreset("←", 0x25),
        KeyPreset("→", 0x27),
    )

    private fun modifiers(): List<KeyPreset> = listOf(
        KeyPreset("Shift", 0x10),
        KeyPreset("Ctrl", 0x11),
        KeyPreset("Alt", 0x12),
        KeyPreset("Win", 0x5B),
        KeyPreset("CapsLock", 0x14),
    )

    private fun basics(): List<KeyPreset> = listOf(
        KeyPreset("Space 空格", 0x20),
        KeyPreset("Enter 回车", 0x0D),
        KeyPreset("Esc", 0x1B),
        KeyPreset("Tab", 0x09),
        KeyPreset("Backspace 删除", 0x08),
    )

    private fun shortcuts(): List<KeyPreset> = listOf(
        KeyPreset("Ctrl+C 复制", 0x43, 2),
        KeyPreset("Ctrl+V 粘贴", 0x56, 2),
        KeyPreset("Ctrl+X 剪切", 0x58, 2),
        KeyPreset("Ctrl+Z 撤销", 0x5A, 2),
        KeyPreset("Ctrl+Y 重做", 0x59, 2),
        KeyPreset("Ctrl+S 保存", 0x53, 2),
        KeyPreset("Ctrl+A 全选", 0x41, 2),
        KeyPreset("Ctrl+F 查找", 0x46, 2),
        KeyPreset("Ctrl+P 打印", 0x50, 2),
        KeyPreset("Ctrl+W 关闭", 0x57, 2),
        KeyPreset("Ctrl+N 新建", 0x4E, 2),
        KeyPreset("Ctrl+T 新标签", 0x54, 2),
        KeyPreset("Alt+Tab 切换", 0x09, 4),
        KeyPreset("Alt+F4 关闭窗", 0x73, 4),
        KeyPreset("Win+D 桌面", 0x44, 8),
        KeyPreset("Win+L 锁屏", 0x4C, 8),
        KeyPreset("Win+E 资源管理", 0x45, 8),
        KeyPreset("Shift+Tab", 0x09, 1),
    )

    private fun mediaAndSystem(): List<KeyPreset> = listOf(
        KeyPreset("唤起键盘", action = ButtonAction.OPEN_KEYBOARD),
        KeyPreset("音量+", action = ButtonAction.VOLUME_UP),
        KeyPreset("音量-", action = ButtonAction.VOLUME_DOWN),
        KeyPreset("关机", action = ButtonAction.SHUTDOWN),
        KeyPreset("静音", 0xAD),
    )

    val all: List<KeyPreset> = buildList {
        addAll(basics())
        addAll(mediaAndSystem())
        addAll(letters())
        addAll(digits())
        addAll(symbols())
        addAll(navigation())
        addAll(modifiers())
        addAll(functionKeys())
        addAll(shortcuts())
    }
}
