package com.yaokongqi.remote.model

/**
 * 内置场景布局方案。id 以 [BUILTIN_ID_PREFIX] 开头，便于识别与合并。
 */
object ScenarioLayoutPresets {
    const val BUILTIN_ID_PREFIX = "builtin-"

    const val ID_VIDEO = "${BUILTIN_ID_PREFIX}video"
    const val ID_TYPING = "${BUILTIN_ID_PREFIX}typing"
    const val ID_OFFICE = "${BUILTIN_ID_PREFIX}office"
    const val ID_PRESENTATION = "${BUILTIN_ID_PREFIX}presentation"
    const val ID_WEB = "${BUILTIN_ID_PREFIX}web"
    const val ID_WINDOW = "${BUILTIN_ID_PREFIX}window"
    const val ID_GAMING = "${BUILTIN_ID_PREFIX}gaming"

    data class Definition(
        val id: String,
        val name: String,
        val description: String,
        val layout: ButtonLayout,
    )

    private fun key(
        id: String,
        label: String,
        row: Int,
        col: Int,
        vk: Int = 0,
        mods: Int = 0,
        action: ButtonAction = ButtonAction.KEY,
        colSpan: Int = 1,
        rowSpan: Int = 1,
    ) = RemoteButton(
        id = id,
        label = label,
        vk = vk,
        mods = mods,
        action = action,
        colSpan = colSpan,
        rowSpan = rowSpan,
        gridRow = row,
        gridCol = col,
    )

    val definitions: List<Definition> = listOf(
        Definition(
            id = ID_VIDEO,
            name = "刷视频",
            description = "上排音量±与左右切条，下排上下翻页，适合短视频",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_4X2,
                columns = 4,
                buttons = listOf(
                    key("${ID_VIDEO}-vol-up", "音量+", 0, 0, action = ButtonAction.VOLUME_UP),
                    key("${ID_VIDEO}-vol-down", "音量-", 0, 1, action = ButtonAction.VOLUME_DOWN),
                    key("${ID_VIDEO}-left", "←", 0, 2, vk = 0x25),
                    key("${ID_VIDEO}-right", "→", 0, 3, vk = 0x27),
                    key("${ID_VIDEO}-up", "↑", 1, 1, vk = 0x26),
                    key("${ID_VIDEO}-down", "↓", 1, 2, vk = 0x28),
                ),
            ),
        ),
        Definition(
            id = ID_TYPING,
            name = "打字",
            description = "翻页、修饰键与唤起键盘，适合文档编辑",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_3X3,
                columns = 3,
                buttons = listOf(
                    key("${ID_TYPING}-esc", "Esc", 0, 0, vk = 0x1B),
                    key("${ID_TYPING}-pgup", "PageUp", 0, 1, vk = 0x21),
                    key("${ID_TYPING}-pgdn", "PageDown", 0, 2, vk = 0x22),
                    key("${ID_TYPING}-shift", "Shift", 1, 0, vk = 0x10),
                    key("${ID_TYPING}-win", "Win", 1, 1, vk = 0x5B),
                    key("${ID_TYPING}-del", "Delete", 1, 2, vk = 0x2E),
                    key("${ID_TYPING}-kbd", "唤起键盘", 2, 0, action = ButtonAction.OPEN_KEYBOARD, colSpan = 2),
                    key("${ID_TYPING}-enter", "Enter", 2, 2, vk = 0x0D),
                ),
            ),
        ),
        Definition(
            id = ID_OFFICE,
            name = "办公编辑",
            description = "复制粘贴、保存撤销等常用快捷键",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_3X3,
                columns = 3,
                buttons = listOf(
                    key("${ID_OFFICE}-copy", "复制", 0, 0, vk = 0x43, mods = 2),
                    key("${ID_OFFICE}-paste", "粘贴", 0, 1, vk = 0x56, mods = 2),
                    key("${ID_OFFICE}-undo", "撤销", 0, 2, vk = 0x5A, mods = 2),
                    key("${ID_OFFICE}-save", "保存", 1, 0, vk = 0x53, mods = 2),
                    key("${ID_OFFICE}-find", "查找", 1, 1, vk = 0x46, mods = 2),
                    key("${ID_OFFICE}-select", "全选", 1, 2, vk = 0x41, mods = 2),
                    key("${ID_OFFICE}-kbd", "唤起键盘", 2, 0, action = ButtonAction.OPEN_KEYBOARD, colSpan = 2),
                    key("${ID_OFFICE}-enter", "Enter", 2, 2, vk = 0x0D),
                ),
            ),
        ),
        Definition(
            id = ID_PRESENTATION,
            name = "演示汇报",
            description = "翻页与放映控制，适合 PPT 演示",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_2X2,
                columns = 2,
                buttons = listOf(
                    key("${ID_PRESENTATION}-left", "←", 0, 0, vk = 0x25),
                    key("${ID_PRESENTATION}-right", "→", 0, 1, vk = 0x27),
                    key("${ID_PRESENTATION}-f5", "F5", 1, 0, vk = 0x74),
                    key("${ID_PRESENTATION}-esc", "Esc", 1, 1, vk = 0x1B),
                ),
            ),
        ),
        Definition(
            id = ID_WEB,
            name = "网页浏览",
            description = "前进后退、标签页与翻页，适合浏览器",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_3X3,
                columns = 3,
                buttons = listOf(
                    key("${ID_WEB}-back", "后退", 0, 0, vk = 0x25, mods = 4),
                    key("${ID_WEB}-forward", "前进", 0, 1, vk = 0x27, mods = 4),
                    key("${ID_WEB}-refresh", "刷新", 0, 2, vk = 0x52, mods = 2),
                    key("${ID_WEB}-new-tab", "新标签", 1, 0, vk = 0x54, mods = 2),
                    key("${ID_WEB}-close-tab", "关标签", 1, 1, vk = 0x57, mods = 2),
                    key("${ID_WEB}-switch", "切窗口", 1, 2, vk = 0x09, mods = 4),
                    key("${ID_WEB}-pgup", "PageUp", 2, 0, vk = 0x21),
                    key("${ID_WEB}-pgdn", "PageDown", 2, 1, vk = 0x22),
                    key("${ID_WEB}-space", "空格", 2, 2, vk = 0x20),
                ),
            ),
        ),
        Definition(
            id = ID_WINDOW,
            name = "窗口切换",
            description = "切换应用、桌面与常用系统快捷键",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_3X3,
                columns = 3,
                buttons = listOf(
                    key("${ID_WINDOW}-alt-tab", "Alt+Tab", 0, 0, vk = 0x09, mods = 4),
                    key("${ID_WINDOW}-desktop", "Win+D", 0, 1, vk = 0x44, mods = 8),
                    key("${ID_WINDOW}-explorer", "Win+E", 0, 2, vk = 0x45, mods = 8),
                    key("${ID_WINDOW}-close", "Alt+F4", 1, 0, vk = 0x73, mods = 4),
                    key("${ID_WINDOW}-lock", "Win+L", 1, 1, vk = 0x4C, mods = 8),
                    key("${ID_WINDOW}-esc", "Esc", 1, 2, vk = 0x1B),
                    key("${ID_WINDOW}-kbd", "唤起键盘", 2, 0, action = ButtonAction.OPEN_KEYBOARD, colSpan = 2),
                    key("${ID_WINDOW}-enter", "Enter", 2, 2, vk = 0x0D),
                ),
            ),
        ),
        Definition(
            id = ID_GAMING,
            name = "游戏方向",
            description = "方向键 + 空格与 Shift，适合轻量游戏",
            layout = ButtonLayout(
                layoutMode = LayoutMode.GRID_2X2,
                columns = 2,
                buttons = listOf(
                    key("${ID_GAMING}-up", "↑", 0, 0, vk = 0x26),
                    key("${ID_GAMING}-space", "空格", 0, 1, vk = 0x20),
                    key("${ID_GAMING}-left", "←", 1, 0, vk = 0x25),
                    key("${ID_GAMING}-right", "→", 1, 1, vk = 0x27),
                ),
            ),
        ),
    )

    fun all(): List<LayoutPreset> = definitions.map { def ->
        LayoutPreset(id = def.id, name = def.name, layout = def.layout)
    }

    fun isBuiltin(id: String): Boolean = id.startsWith(BUILTIN_ID_PREFIX)

    fun descriptionFor(preset: LayoutPreset): String? =
        definitions.find { it.id == preset.id }?.description
}
