package com.yaokongqi.remote.ui.game

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.yaokongqi.remote.R
import com.yaokongqi.remote.model.GamepadControlId

object GamepadActionIcons {
    @DrawableRes
    fun drawableRes(id: GamepadControlId): Int? = when (id) {
        GamepadControlId.FIRE, GamepadControlId.RT -> R.drawable.ic_gamepad_fire
        GamepadControlId.ADS, GamepadControlId.LT -> R.drawable.ic_gamepad_ads
        GamepadControlId.JUMP, GamepadControlId.A -> R.drawable.ic_gamepad_jump
        GamepadControlId.SLIDE, GamepadControlId.B -> R.drawable.ic_gamepad_slide
        GamepadControlId.RELOAD -> R.drawable.ic_gamepad_reload
        GamepadControlId.HEAL -> R.drawable.ic_gamepad_heal
        GamepadControlId.THROW -> R.drawable.ic_gamepad_throw
        GamepadControlId.TACTICAL, GamepadControlId.LB -> R.drawable.ic_gamepad_tactical
        GamepadControlId.ULTIMATE, GamepadControlId.RB -> R.drawable.ic_gamepad_ultimate
        GamepadControlId.BACKPACK -> R.drawable.ic_gamepad_backpack
        GamepadControlId.INTERACT, GamepadControlId.X -> R.drawable.ic_gamepad_interact
        GamepadControlId.SURVIVAL -> R.drawable.ic_gamepad_survival
        GamepadControlId.WEAPON, GamepadControlId.Y -> R.drawable.ic_gamepad_weapon
        GamepadControlId.MAP -> R.drawable.ic_gamepad_map
        else -> null
    }

    fun fallbackIcon(id: GamepadControlId): ImageVector = Icons.Filled.SwapHoriz
}
