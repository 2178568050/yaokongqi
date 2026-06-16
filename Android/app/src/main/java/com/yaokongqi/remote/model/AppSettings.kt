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
    /** 虚拟手柄兜底发送频率（Hz）；瞄准/按键已即时发送，此项主要同步移动与状态 */
    val gamepadPollHz: Int = 250,
    /** 手柄数据走 UDP 低延迟通道（需 PC 0.2.6+；WebSocket 仍负责配对与控制） */
    val gamepadUseUdp: Boolean = true,
    /** 右瞄准水平灵敏度（legacy：与 aimSensitivityX 同步） */
    val aimSensitivity: Float = 2.05f,
    val aimSensitivityX: Float = 2.05f,
    val aimSensitivityY: Float = 2.00f,
    /** 开镜（LT）时右瞄准水平灵敏度 */
    val adsAimSensitivityX: Float = 1.45f,
    /** 开镜（LT）时右瞄准垂直灵敏度 */
    val adsAimSensitivityY: Float = 1.52f,
    /** 腰射开火（RT、未开镜）水平灵敏度 */
    val fireAimSensitivityX: Float = 1.68f,
    /** 腰射开火（RT、未开镜）垂直灵敏度 */
    val fireAimSensitivityY: Float = 1.85f,
    /** 开镜开火（RT + 开镜）水平灵敏度 */
    val fireAdsAimSensitivityX: Float = 1.05f,
    /** 开镜开火（RT + 开镜）垂直灵敏度 */
    val fireAdsAimSensitivityY: Float = 1.32f,
    /** 左移动轮盘输出倍率 */
    val moveSensitivity: Float = 1.12f,
    /** 瞄准平滑 0~0.5，越低越跟手；微调时自动减弱 */
    val aimSmoothing: Float = 0.05f,
    /** 腰射快速滑动额外加速（0=匀速，越大甩枪越快；开镜/开火为线性） */
    val aimSwipeAcceleration: Float = 0.85f,
    /** 左轮盘 deadzone（0~0.35） */
    val moveDeadzone: Float = 0.07f,
    /** 射击模式控件布局（位置与大小） */
    val gamepadLayout: GamepadLayout = GamepadLayouts.default(),
    /** 手柄按钮/区域空闲透明度（0.15~0.95） */
    val gamepadControlAlpha: Float = 0.50f,
    /** 非固定移动轮盘：按下时在触点出现，松手隐藏 */
    val gamepadFloatingStick: Boolean = false,
    /** 瞄准手指停住后 rx/ry 逐渐归零，避免划完仍持续转动 */
    val aimIdleDecay: Boolean = true,
) {
    companion object {
        const val MOVE_SENS_MIN = 0.35f
        const val MOVE_SENS_MAX = 2.5f
        const val AIM_SENS_X_MIN = 0.35f
        const val AIM_SENS_X_MAX = 6f
        const val AIM_SENS_Y_MIN = 0.35f
        const val AIM_SENS_Y_MAX = 5f
        const val AIM_SWIPE_ACCEL_MIN = 0f
        const val AIM_SWIPE_ACCEL_MAX = 3f
        const val MOVE_DEADZONE_MIN = 0.04f
        const val MOVE_DEADZONE_MAX = 0.30f
        const val AIM_SMOOTHING_MIN = 0f
        const val AIM_SMOOTHING_MAX = 0.5f
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
        fireAimSensitivityX = fireAimSensitivityX.coerceIn(AIM_SENS_X_MIN, AIM_SENS_X_MAX),
        fireAimSensitivityY = fireAimSensitivityY.coerceIn(AIM_SENS_Y_MIN, AIM_SENS_Y_MAX),
        fireAdsAimSensitivityX = fireAdsAimSensitivityX.coerceIn(AIM_SENS_X_MIN, AIM_SENS_X_MAX),
        fireAdsAimSensitivityY = fireAdsAimSensitivityY.coerceIn(AIM_SENS_Y_MIN, AIM_SENS_Y_MAX),
        moveSensitivity = moveSensitivity.coerceIn(MOVE_SENS_MIN, MOVE_SENS_MAX),
        aimSwipeAcceleration = aimSwipeAcceleration.coerceIn(AIM_SWIPE_ACCEL_MIN, AIM_SWIPE_ACCEL_MAX),
        aimSmoothing = aimSmoothing.coerceIn(AIM_SMOOTHING_MIN, AIM_SMOOTHING_MAX),
        moveDeadzone = moveDeadzone.coerceIn(MOVE_DEADZONE_MIN, MOVE_DEADZONE_MAX),
        gamepadControlAlpha = gamepadControlAlpha.coerceIn(GAMEPAD_ALPHA_MIN, GAMEPAD_ALPHA_MAX),
        gamepadPollHz = gamepadPollHz.coerceIn(60, 500),
        gamepadLayout = gamepadLayout.normalized(),
    )
}
