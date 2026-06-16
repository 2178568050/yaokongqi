package com.yaokongqi.remote.ui.util

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** 启动后尽量拉高显示 / 触摸采样刷新率（设备支持时生效） */
object DisplayPerformance {
    const val TARGET_GAMEPAD_HZ = 120f

    fun applyMaxPerformance(activity: Activity, targetHz: Float = TARGET_GAMEPAD_HZ) {
        applyHighestRefreshMode(activity, targetHz)
        lockFrameRate(activity, targetHz)
        keepScreenOn(activity)
    }

    private fun applyHighestRefreshMode(activity: Activity, targetHz: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = activity.display ?: return
        val modes = display.supportedModes
        if (modes.isEmpty()) return

        val preferred = modes.maxByOrNull { it.refreshRate } ?: return

        val params = activity.window.attributes
        if (params.preferredDisplayModeId != preferred.modeId) {
            params.preferredDisplayModeId = preferred.modeId
            params.preferredRefreshRate = preferred.refreshRate
            activity.window.attributes = params
        }
    }

    private fun lockFrameRate(activity: Activity, targetHz: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val displayHz = activity.display?.mode?.refreshRate ?: targetHz
            val hz = maxOf(targetHz, displayHz)
            val params = activity.window.attributes
            if (abs(params.preferredRefreshRate - hz) > 0.5f) {
                params.preferredRefreshRate = hz
                activity.window.attributes = params
            }
        }
    }

    private fun keepScreenOn(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun currentRefreshRate(activity: Activity): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.display?.mode?.refreshRate ?: 0f
        } else {
            0f
        }.let { hz ->
            if (hz <= 0f) TARGET_GAMEPAD_HZ else hz
        }
    }

    fun refreshRateLabel(activity: Activity): String {
        val hz = currentRefreshRate(activity)
        return if (abs(hz - hz.roundToInt()) < 0.05f) {
            "${hz.roundToInt()} Hz"
        } else {
            String.format("%.1f Hz", hz)
        }
    }
}
