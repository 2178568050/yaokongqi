package com.yaokongqi.remote.ui.game

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yaokongqi.remote.model.AppSettings

/** 全局 + 单键透明度合成（与设置滑条一致） */
fun resolveGamepadControlAlpha(
    globalControlAlpha: Float,
    placementOpacity: Float,
): Float = (
    globalControlAlpha.coerceIn(AppSettings.GAMEPAD_ALPHA_MIN, AppSettings.GAMEPAD_ALPHA_MAX) *
        placementOpacity.coerceIn(0.15f, 1f)
    ).coerceIn(AppSettings.GAMEPAD_ALPHA_MIN, AppSettings.GAMEPAD_ALPHA_MAX)

fun gamepadIconDisplayAlpha(baseAlpha: Float, pressed: Boolean): Float =
    if (pressed) (baseAlpha + 0.35f).coerceAtMost(1f) else baseAlpha

@Composable
fun GamepadActionIconImage(
    @DrawableRes drawableRes: Int,
    contentDescription: String,
    displayAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .alpha(displayAlpha.coerceIn(0f, 1f)),
    )
}
