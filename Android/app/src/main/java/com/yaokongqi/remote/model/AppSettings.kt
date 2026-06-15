package com.yaokongqi.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val layoutMode: LayoutMode = LayoutMode.GRID_3X3,
    val touchScrollMode: Boolean = false,
    val touchSensitivity: TouchSensitivity = TouchSensitivity.MEDIUM,
    val pinchToMinimalEnabled: Boolean = true,
    val darkMode: Boolean = true,
    val hapticFeedback: Boolean = true,
    val showTouchpadHint: Boolean = false,
    /** 横屏时左右分栏：左侧按键、中间留白、右侧触摸板 */
    val landscapeSplitLayout: Boolean = true,
    /** 连接时在通知栏显示前台服务通知（有助于锁屏保活） */
    val showConnectionNotification: Boolean = true,
    /** 横屏射击游戏：虚拟 Xbox 手柄（需 PC 安装 ViGEmBus） */
    val shooterGamepadMode: Boolean = false,
    /** 虚拟手柄状态发送频率（Hz） */
    val gamepadPollHz: Int = 180,
    /** 右瞄准水平灵敏度（legacy：aimSensitivity 与 X 同步） */
    val aimSensitivity: Float = 2.20f,
    val aimSensitivityX: Float = 2.20f,
    val aimSensitivityY: Float = 1.65f,
    /** 开镜（LT）时右瞄准水平灵敏度 */
    val adsAimSensitivityX: Float = 1.55f,
    /** 开镜（LT）时右瞄准垂直灵敏度 */
    val adsAimSensitivityY: Float = 1.20f,
    /** 左移动轮盘输出倍率 */
    val moveSensitivity: Float = 1.10f,
    /** 瞄准微抖动过滤 0~0.75，越低越跟手 */
    val aimSmoothing: Float = 0.18f,
    /** 快速滑动额外加速（0=匀速，越大甩枪/转身越快） */
    val aimSwipeAcceleration: Float = 1.25f,
    /** 左轮盘 deadzone（0~0.45） */
    val moveDeadzone: Float = 0.08f,
    /** 右瞄准松手后每帧衰减系数（越大滑停越慢） */
    val aimDecay: Float = 0.68f,
    /** 射击模式控件布局（位置与大小） */
    val gamepadLayout: GamepadLayout = GamepadLayouts.default(),
    /** 手柄按钮/区域空闲透明度（0.15~0.95） */
    val gamepadControlAlpha: Float = 0.50f,
    /** 非固定移动轮盘：按下时在触点出现，松手隐藏 */
    val gamepadFloatingStick: Boolean = false,
) {
    companion object {
        const val MOVE_SENS_MIN = 0.25f
        const val MOVE_SENS_MAX = 3.5f
        const val AIM_SENS_X_MIN = 0.20f
        const val AIM_SENS_X_MAX = 12f
        const val AIM_SENS_Y_MIN = 0.20f
        const val AIM_SENS_Y_MAX = 8f
        const val AIM_SWIPE_ACCEL_MIN = 0f
        const val AIM_SWIPE_ACCEL_MAX = 4f
        const val MOVE_DEADZONE_MIN = 0.05f
        const val MOVE_DEADZONE_MAX = 0.35f
        const val AIM_SMOOTHING_MIN = 0f
        const val AIM_SMOOTHING_MAX = 0.75f
        const val AIM_DECAY_MIN = 0.55f
        const val AIM_DECAY_MAX = 0.92f
        const val GAMEPAD_ALPHA_MIN = 0.15f
        const val GAMEPAD_ALPHA_MAX = 0.95f
    }

    /** 保存前将滑条数值收拢到合法区间，避免旧版本或异常数据越界 */
    fun clamped(): AppSettings = copy(
        aimSensitivity = aimSensitivity.coerceIn(AIM_SENS_X_MIN, AIM_SENS_X_MAX),
        aimSensitivityX = aimSensitivityX.coerceIn(AIM_SENS_X_MIN, AIM_SENS_X_MAX),
        aimSensitivityY = aimSensitivityY.coerceIn(AIM_SENS_Y_MIN, AIM_SENS_Y_MAX),
        adsAimSensitivityX = adsAimSensitivityX.coerceIn(AIM_SENS_X_MIN, AIM_SENS_X_MAX),
        adsAimSensitivityY = adsAimSensitivityY.coerceIn(AIM_SENS_Y_MIN, AIM_SENS_Y_MAX),
        moveSensitivity = moveSensitivity.coerceIn(MOVE_SENS_MIN, MOVE_SENS_MAX),
        aimSwipeAcceleration = aimSwipeAcceleration.coerceIn(AIM_SWIPE_ACCEL_MIN, AIM_SWIPE_ACCEL_MAX),
        aimSmoothing = aimSmoothing.coerceIn(AIM_SMOOTHING_MIN, AIM_SMOOTHING_MAX),
        moveDeadzone = moveDeadzone.coerceIn(MOVE_DEADZONE_MIN, MOVE_DEADZONE_MAX),
        aimDecay = aimDecay.coerceIn(AIM_DECAY_MIN, AIM_DECAY_MAX),
        gamepadControlAlpha = gamepadControlAlpha.coerceIn(GAMEPAD_ALPHA_MIN, GAMEPAD_ALPHA_MAX),
        gamepadPollHz = gamepadPollHz.coerceIn(60, 250),
        gamepadLayout = gamepadLayout.normalized(),
    )
}
