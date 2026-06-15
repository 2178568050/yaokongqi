package com.yaokongqi.remote.ui.game

import com.yaokongqi.remote.model.GamepadButtons
import com.yaokongqi.remote.model.GamepadControlId

/** Apex 默认手柄预设：屏幕语义键 → 虚拟 Xbox 输入 */
object GamepadActionBindings {
    fun apply(engine: GamepadInputEngine, id: GamepadControlId, pressed: Boolean) {
        when (id) {
            GamepadControlId.FIRE, GamepadControlId.RT -> engine.setRightTrigger(pressed)
            GamepadControlId.ADS, GamepadControlId.LT -> engine.setLeftTrigger(pressed)
            GamepadControlId.JUMP, GamepadControlId.A -> engine.setButton(GamepadButtons.A, pressed)
            GamepadControlId.SLIDE, GamepadControlId.B -> engine.setButton(GamepadButtons.B, pressed)
            GamepadControlId.RELOAD,
            GamepadControlId.INTERACT,
            GamepadControlId.X,
            -> engine.setButton(GamepadButtons.X, pressed)
            GamepadControlId.TACTICAL, GamepadControlId.LB -> engine.setButton(GamepadButtons.LB, pressed)
            GamepadControlId.ULTIMATE, GamepadControlId.RB -> engine.setButton(GamepadButtons.RB, pressed)
            GamepadControlId.THROW -> engine.setButton(GamepadButtons.RIGHT, pressed)
            GamepadControlId.HEAL -> engine.setButton(GamepadButtons.UP, pressed)
            GamepadControlId.SURVIVAL -> engine.setButton(GamepadButtons.LEFT, pressed)
            GamepadControlId.WEAPON, GamepadControlId.Y -> engine.setButton(GamepadButtons.Y, pressed)
            GamepadControlId.BACKPACK -> engine.setButton(GamepadButtons.START, pressed)
            GamepadControlId.MAP -> engine.setButton(GamepadButtons.BACK, pressed)
            else -> Unit
        }
    }
}
