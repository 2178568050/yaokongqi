package com.yaokongqi.remote.model

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GamepadControlId {
    @SerialName("move")
    MOVE_STICK,

    @SerialName("aim")
    AIM_ZONE,

    /** @deprecated 旧布局兼容 */
    @SerialName("lb")
    LB,

    @SerialName("lt")
    LT,

    @SerialName("rb")
    RB,

    @SerialName("rt")
    RT,

    @SerialName("y")
    Y,

    @SerialName("x")
    X,

    @SerialName("b")
    B,

    @SerialName("a")
    A,

    @SerialName("fire")
    FIRE,

    @SerialName("ads")
    ADS,

    @SerialName("jump")
    JUMP,

    @SerialName("slide")
    SLIDE,

    @SerialName("reload")
    RELOAD,

    @SerialName("tactical")
    TACTICAL,

    @SerialName("ultimate")
    ULTIMATE,

    @SerialName("throw")
    THROW,

    @SerialName("interact")
    INTERACT,

    @SerialName("backpack")
    BACKPACK,

    @SerialName("map")
    MAP,

    /** D-pad 上：医疗/护盾 */
    @SerialName("heal")
    HEAL,

    /** D-pad 左：求生装备 */
    @SerialName("survival")
    SURVIVAL,

    /** Y 键：切换武器 */
    @SerialName("weapon")
    WEAPON,
}

@Serializable
data class GamepadControlPlacement(
    val id: GamepadControlId,
    /** 屏幕宽度比例 0~1 */
    val centerX: Float,
    /** 屏幕高度比例 0~1 */
    val centerY: Float,
    /** 圆形按钮直径；区域控件作 fallback */
    val sizeDp: Float = 56f,
    /** 区域宽度（移动轮盘 / 瞄准区） */
    val widthDp: Float = 160f,
    /** 区域高度 */
    val heightDp: Float = 160f,
    /** 单键透明度系数 0.15~1，与全局控件透明度相乘 */
    val opacity: Float = 1f,
) {
    val isZone: Boolean
        get() = id == GamepadControlId.MOVE_STICK || id == GamepadControlId.AIM_ZONE

    val accent: Boolean
        get() = id == GamepadControlId.FIRE ||
            id == GamepadControlId.ADS ||
            id == GamepadControlId.RT ||
            id == GamepadControlId.LT ||
            id == GamepadControlId.A ||
            id == GamepadControlId.JUMP

    fun effectiveAlpha(globalAlpha: Float): Float =
        (globalAlpha.coerceIn(0.15f, 0.95f) * opacity.coerceIn(0.15f, 1f)).coerceIn(0.15f, 0.95f)
}

@Serializable
data class GamepadLayout(
    val controls: List<GamepadControlPlacement> = GamepadLayouts.defaultControls(),
) {
    fun placement(id: GamepadControlId): GamepadControlPlacement =
        controls.find { it.id == id } ?: GamepadLayouts.defaultControl(id)

    fun update(id: GamepadControlId, transform: (GamepadControlPlacement) -> GamepadControlPlacement): GamepadLayout {
        val updated = controls.map { if (it.id == id) transform(it) else it }
        return copy(controls = updated)
    }

    /** 可拖动布局的按键（不含固定半屏区域）；自动补全默认包中缺失的键 */
    fun buttonControls(): List<GamepadControlPlacement> = normalized().controls.filter { !it.isZone }

    fun moveStickPlacement(): GamepadControlPlacement =
        normalized().controls.find { it.id == GamepadControlId.MOVE_STICK }
            ?: GamepadLayouts.defaultMoveStick().let {
                it.copy(sizeDp = GamepadSizeLimits.clampButtonSize(it.id, it.sizeDp))
            }

    /** 持久化保存：去重后的战斗按键 + 移动轮盘 */
    fun layoutControls(): List<GamepadControlPlacement> =
        buttonControls() + moveStickPlacement()

    /** 合并 legacy Xbox 键位（RT/LT/A…）与语义键（开火/开镜…），避免重复显示 */
    fun normalized(): GamepadLayout {
        val zones = controls.filter { it.isZone }.distinctBy { it.id }
        val merged = LinkedHashMap<GamepadControlId, GamepadControlPlacement>()
        for (placement in controls.filter { !it.isZone }) {
            val canonicalId = placement.id.canonicalId()
            val candidate = if (placement.id == canonicalId) {
                placement
            } else {
                placement.copy(id = canonicalId)
            }
            val existing = merged[canonicalId]
            if (existing == null || existing.id.isLegacyXboxButton() && !candidate.id.isLegacyXboxButton()) {
                merged[canonicalId] = candidate.copy(
                    sizeDp = GamepadSizeLimits.clampButtonSize(canonicalId, candidate.sizeDp),
                )
            }
        }
        val buttons = merged.values.toList()
        val existingIds = buttons.map { it.id }.toSet()
        val missing = GamepadLayouts.defaultButtonControls().filter { it.id !in existingIds }
        return copy(controls = zones + buttons + missing)
    }
}

private val LEGACY_TO_CANONICAL = mapOf(
    GamepadControlId.RT to GamepadControlId.FIRE,
    GamepadControlId.LT to GamepadControlId.ADS,
    GamepadControlId.A to GamepadControlId.JUMP,
    GamepadControlId.B to GamepadControlId.SLIDE,
    GamepadControlId.X to GamepadControlId.INTERACT,
    GamepadControlId.LB to GamepadControlId.TACTICAL,
    GamepadControlId.RB to GamepadControlId.ULTIMATE,
    GamepadControlId.Y to GamepadControlId.WEAPON,
)

private fun GamepadControlId.canonicalId(): GamepadControlId =
    LEGACY_TO_CANONICAL[this] ?: this

private fun GamepadControlId.isLegacyXboxButton(): Boolean =
    this in LEGACY_TO_CANONICAL

object GamepadSizeLimits {
    /** 100% 参照直径，用于编辑界面百分比显示 */
    const val BUTTON_BASE_DP = 56f
    const val BUTTON_MIN_DP = 36f
    const val BUTTON_MAX_DP = BUTTON_BASE_DP * 2.5f // 250%

    const val MOVE_STICK_MIN_DP = 72f
    const val MOVE_STICK_MAX_DP = 108f * 2.5f // 270dp

    fun buttonSizePercent(sizeDp: Float): Int =
        (sizeDp / BUTTON_BASE_DP * 100f).roundToInt().coerceIn(64, 250)

    fun clampButtonSize(id: GamepadControlId, sizeDp: Float): Float = when (id) {
        GamepadControlId.MOVE_STICK -> sizeDp.coerceIn(MOVE_STICK_MIN_DP, MOVE_STICK_MAX_DP)
        else -> sizeDp.coerceIn(BUTTON_MIN_DP, BUTTON_MAX_DP)
    }
}

object GamepadLayouts {
    fun default(): GamepadLayout = GamepadLayout(
        defaultButtonControls() + defaultMoveStick(),
    )

    fun defaultControls(): List<GamepadControlPlacement> =
        defaultButtonControls() + defaultMoveStick()

    /** Apex 默认包：右半屏战斗区布局，避开左上角系统栏与左下移动轮盘 */
    fun defaultButtonControls(): List<GamepadControlPlacement> = listOf(
        // 主战斗（右下弧）
        GamepadControlPlacement(id = GamepadControlId.FIRE, centerX = 0.87f, centerY = 0.82f, sizeDp = 70f),
        GamepadControlPlacement(id = GamepadControlId.JUMP, centerX = 0.93f, centerY = 0.66f, sizeDp = 54f),
        GamepadControlPlacement(id = GamepadControlId.SLIDE, centerX = 0.78f, centerY = 0.90f, sizeDp = 50f),
        // 中右功能区
        GamepadControlPlacement(id = GamepadControlId.RELOAD, centerX = 0.66f, centerY = 0.74f, sizeDp = 46f),
        GamepadControlPlacement(id = GamepadControlId.INTERACT, centerX = 0.58f, centerY = 0.84f, sizeDp = 48f),
        GamepadControlPlacement(id = GamepadControlId.ULTIMATE, centerX = 0.66f, centerY = 0.62f, sizeDp = 48f),
        GamepadControlPlacement(id = GamepadControlId.THROW, centerX = 0.72f, centerY = 0.58f, sizeDp = 46f),
        // D-pad / 切枪（右半屏上部）
        GamepadControlPlacement(id = GamepadControlId.HEAL, centerX = 0.54f, centerY = 0.40f, sizeDp = 44f),
        GamepadControlPlacement(id = GamepadControlId.SURVIVAL, centerX = 0.54f, centerY = 0.52f, sizeDp = 44f),
        GamepadControlPlacement(id = GamepadControlId.WEAPON, centerX = 0.64f, centerY = 0.46f, sizeDp = 46f),
        // 右上角：开镜 + 战术技能（拇指易触达）
        GamepadControlPlacement(id = GamepadControlId.ADS, centerX = 0.88f, centerY = 0.14f, sizeDp = 62f),
        GamepadControlPlacement(id = GamepadControlId.TACTICAL, centerX = 0.78f, centerY = 0.14f, sizeDp = 48f),
        // 系统键（顶部内侧，避开编辑栏）
        GamepadControlPlacement(id = GamepadControlId.MAP, centerX = 0.58f, centerY = 0.14f, sizeDp = 42f),
        GamepadControlPlacement(id = GamepadControlId.BACKPACK, centerX = 0.68f, centerY = 0.14f, sizeDp = 42f),
    )

    fun defaultMoveStick(): GamepadControlPlacement = GamepadControlPlacement(
        id = GamepadControlId.MOVE_STICK,
        centerX = 0.20f,
        centerY = 0.78f,
        sizeDp = 108f,
    )

    fun defaultControl(id: GamepadControlId): GamepadControlPlacement = when (id) {
        GamepadControlId.MOVE_STICK -> defaultMoveStick()
        else -> defaultButtonControls().find { it.id == id }
            ?: GamepadControlPlacement(id = id, centerX = 0.5f, centerY = 0.5f, sizeDp = 52f)
    }

    fun label(id: GamepadControlId): String = when (id) {
        GamepadControlId.MOVE_STICK -> "移动"
        GamepadControlId.AIM_ZONE -> "瞄准"
        GamepadControlId.FIRE, GamepadControlId.RT -> "射击"
        GamepadControlId.ADS, GamepadControlId.LT -> "开镜"
        GamepadControlId.JUMP, GamepadControlId.A -> "跳跃"
        GamepadControlId.SLIDE, GamepadControlId.B -> "蹲下"
        GamepadControlId.RELOAD -> "换弹"
        GamepadControlId.INTERACT, GamepadControlId.X -> "互动"
        GamepadControlId.TACTICAL, GamepadControlId.LB -> "战术"
        GamepadControlId.ULTIMATE -> "绝招"
        GamepadControlId.THROW -> "投掷"
        GamepadControlId.RB -> "RB"
        GamepadControlId.HEAL -> "治疗"
        GamepadControlId.SURVIVAL -> "求生"
        GamepadControlId.WEAPON, GamepadControlId.Y -> "切枪"
        GamepadControlId.BACKPACK -> "背包"
        GamepadControlId.MAP -> "地图"
        GamepadControlId.LB -> "LB"
        GamepadControlId.LT -> "LT"
        GamepadControlId.RB -> "RB"
        GamepadControlId.RT -> "RT"
        GamepadControlId.X -> "X"
        GamepadControlId.B -> "B"
        GamepadControlId.A -> "A"
    }
}
