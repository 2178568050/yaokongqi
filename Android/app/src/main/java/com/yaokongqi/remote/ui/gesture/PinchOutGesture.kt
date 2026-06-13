package com.yaokongqi.remote.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

private const val MIN_PINCH_SPAN_PX = 48f
private const val PINCH_OUT_RATIO = 1.22f

fun Modifier.onTwoFingerPinchOut(
    enabled: Boolean = true,
    onPinchOut: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            var initialDistance: Float? = null
            var triggered = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                when {
                    pressed.size >= 2 -> {
                        val dist = hypot(
                            pressed[0].position.x - pressed[1].position.x,
                            pressed[0].position.y - pressed[1].position.y,
                        )
                        if (initialDistance == null) {
                            if (dist >= MIN_PINCH_SPAN_PX) initialDistance = dist
                        } else if (!triggered && dist / initialDistance >= PINCH_OUT_RATIO) {
                            triggered = true
                            onPinchOut()
                        }
                    }
                    pressed.isEmpty() -> break
                    else -> {
                        initialDistance = null
                        triggered = false
                    }
                }
            }
        }
    }
}
