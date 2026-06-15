package com.yaokongqi.remote.ui.components

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 射击游戏模式：隐藏状态栏/导航栏，降低全面屏手势误触退出概率。
 */
@Composable
fun GameImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    if (!enabled) return

    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        if (activity == null) {
            return@DisposableEffect onDispose {}
        }
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)

        val prevStatusColor = window.statusBarColor
        val prevNavColor = window.navigationBarColor
        val prevNavDividerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor
        } else {
            AndroidColor.TRANSPARENT
        }
        val prevNavContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            true
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        val insetsListener = android.view.View.OnApplyWindowInsetsListener { v, insets ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            v.onApplyWindowInsets(insets)
        }
        view.setOnApplyWindowInsetsListener(insetsListener)

        onDispose {
            view.setOnApplyWindowInsetsListener(null)
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = prevStatusColor
            window.navigationBarColor = prevNavColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = prevNavDividerColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = prevNavContrastEnforced
            }
        }
    }
}
