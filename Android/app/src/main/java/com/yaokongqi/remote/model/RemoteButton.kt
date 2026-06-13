package com.yaokongqi.remote.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RemoteButton(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val vk: Int = 0,
    val mods: Int = 0,
    val action: ButtonAction = ButtonAction.KEY,
    /** 占据列数，1..gridColumns */
    val colSpan: Int = 1,
    /** 占据行数，1..gridRows */
    val rowSpan: Int = 1,
    /** 网格起始列，-1 表示自动排列 */
    val gridCol: Int = -1,
    /** 网格起始行，-1 表示自动排列 */
    val gridRow: Int = -1,
)

@Serializable
data class ButtonLayout(
    val layoutMode: LayoutMode = LayoutMode.GRID_3X3,
    val columns: Int = 3,
    val buttons: List<RemoteButton> = defaultButtons(),
) {
    fun effectiveColumns(): Int = layoutMode.columns

    fun maxButtons(): Int = layoutMode.maxButtons

    companion object {
        fun defaultButtons(): List<RemoteButton> = listOf(
            RemoteButton(label = "复制", vk = 0x43, mods = 2),
            RemoteButton(label = "粘贴", vk = 0x56, mods = 2),
            RemoteButton(label = "撤销", vk = 0x5A, mods = 2),
            RemoteButton(label = "保存", vk = 0x53, mods = 2),
            RemoteButton(label = "Enter", vk = 0x0D),
            RemoteButton(label = "Esc", vk = 0x1B),
            RemoteButton(label = "空格", vk = 0x20),
            RemoteButton(label = "删除", vk = 0x08),
            RemoteButton(label = "Tab", vk = 0x09),
        )
    }
}
