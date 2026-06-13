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
)
