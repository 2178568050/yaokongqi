@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yaokongqi.remote.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.yaokongqi.remote.ui.game.HighRateTouch.forEachDeltaSample
import kotlin.math.abs
import kotlin.math.hypot

private const val SWIPE_THRESHOLD_PX = 100f
private const val WHEEL_DELTA = 120
private const val MOVE_DEAD_ZONE = 5f
private const val LONG_PRESS_MS = 450L

data class TrackpadCallbacks(
    val onMove: (dx: Float, dy: Float) -> Unit,
    val onScroll: (deltaY: Int, deltaX: Int) -> Unit,
    val onSingleClick: () -> Unit,
    val onDoubleClick: () -> Unit,
    val onRightClick: () -> Unit,
    val onLongPress: (() -> Unit)? = null,
    val onThreeFingerSwipeLeft: () -> Unit = {},
    val onThreeFingerSwipeRight: () -> Unit = {},
    val onThreeFingerSwipeUp: () -> Unit = {},
    /** 多指（≥3）按下时回调，用于阻止父级/系统抢手势 */
    val onMultiTouchActive: (Boolean) -> Unit = {},
    /** 手势结束，刷掉鼠标移动亚像素余量 */
    val onGestureEnd: () -> Unit = {},
)

fun Modifier.laptopTrackpadGestures(
    sessionKey: Int,
    scrollOnly: Boolean,
    scrollMode: Boolean,
    scrollMultiplier: Float,
    callbacks: TrackpadCallbacks,
): Modifier = pointerInput(sessionKey, scrollOnly, scrollMode, scrollMultiplier) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val positions = mutableMapOf<PointerId, Offset>()
        positions[firstDown.id] = firstDown.position

        var totalMoveX = 0f
        var totalMoveY = 0f
        var accumX = 0f
        var accumY = 0f
        var moved = false
        var maxFingers = 1
        var longPressTriggered = false
        var multiTouchSteal = false
        var lastEventCancelled = false

        TrackpadLongPressController.schedule(LONG_PRESS_MS) {
            if (!moved && maxFingers == 1 && !longPressTriggered) {
                longPressTriggered = true
                if (scrollOnly) {
                    callbacks.onLongPress?.invoke()
                } else {
                    callbacks.onRightClick()
                }
            }
        }

        try {
            do {
                val pass = if (multiTouchSteal) PointerEventPass.Initial else PointerEventPass.Main
                val event = awaitPointerEvent(pass)
                val pressed = event.changes.filter { it.pressed }
                maxFingers = maxOf(maxFingers, pressed.size)

                if (maxFingers != 1 || moved) {
                    TrackpadLongPressController.cancelAll()
                }

                if (maxFingers >= 3 && !multiTouchSteal) {
                    multiTouchSteal = true
                    callbacks.onMultiTouchActive(true)
                }

                if (multiTouchSteal) {
                    pressed.forEach { it.consume() }
                }

                pressed.forEach { change ->
                    if (change.positionChanged()) {
                        change.forEachDeltaSample { dx, dy, _ ->
                            totalMoveX += dx
                            totalMoveY += dy
                            accumX += dx
                            accumY += dy
                            if (hypot(totalMoveX, totalMoveY) > MOVE_DEAD_ZONE) moved = true
                        }
                        if (!multiTouchSteal) change.consume()
                    }
                    positions[change.id] = change.position
                }

                event.changes.filter { !it.pressed }.forEach { change ->
                    positions.remove(change.id)
                }

                lastEventCancelled = event.changes.any { change ->
                    change.previousPressed && !change.pressed && !change.changedToUp()
                }

                val fingers = pressed.size

                when {
                    scrollOnly -> {
                        flushScroll(accumX, accumY, scrollMultiplier, callbacks.onScroll).also { (nx, ny) ->
                            accumX = nx
                            accumY = ny
                        }
                    }
                    fingers >= 2 -> {
                        flushScroll(accumX, accumY, scrollMultiplier, callbacks.onScroll).also { (nx, ny) ->
                            accumX = nx
                            accumY = ny
                        }
                    }
                    fingers == 1 -> {
                        if (scrollMode && abs(accumY) > abs(accumX)) {
                            val scrollStep = (-accumY * scrollMultiplier).toInt()
                            if (scrollStep != 0) {
                                callbacks.onScroll(scrollStep * WHEEL_DELTA / 8, 0)
                                accumY = 0f
                            }
                            flushMove(accumX, 0f, callbacks.onMove).also { accumX = it.first }
                        } else {
                            flushMove(accumX, accumY, callbacks.onMove).also {
                                accumX = it.first
                                accumY = it.second
                            }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        } finally {
            TrackpadLongPressController.cancelAll()
            if (multiTouchSteal) {
                callbacks.onMultiTouchActive(false)
            }
            flushMove(accumX, accumY, callbacks.onMove)
            callbacks.onGestureEnd()
        }

        if (lastEventCancelled) return@awaitEachGesture

        if (longPressTriggered) return@awaitEachGesture

        if (!moved && !scrollOnly) {
            when (maxFingers) {
                1 -> callbacks.onSingleClick()
                2 -> callbacks.onRightClick()
            }
            return@awaitEachGesture
        }

        dispatchMultiFingerSwipe(maxFingers, totalMoveX, totalMoveY, callbacks)
    }
}

private fun dispatchMultiFingerSwipe(
    maxFingers: Int,
    totalMoveX: Float,
    totalMoveY: Float,
    callbacks: TrackpadCallbacks,
) {
    val distance = hypot(totalMoveX, totalMoveY)
    if (distance < SWIPE_THRESHOLD_PX) return

    // 四指优先：多数机型三指被系统占用（截图等）
    val fingerTier = when {
        maxFingers >= 4 -> 4
        maxFingers >= 3 -> 3
        else -> return
    }

    when {
        abs(totalMoveX) > abs(totalMoveY) -> {
            if (totalMoveX > 0) callbacks.onThreeFingerSwipeRight()
            else callbacks.onThreeFingerSwipeLeft()
        }
        totalMoveY < -SWIPE_THRESHOLD_PX -> callbacks.onThreeFingerSwipeUp()
        fingerTier >= 4 && totalMoveY > SWIPE_THRESHOLD_PX -> {
            // 四指下滑：切换上一个窗口（Alt+Shift+Tab），避免与上滑 Win+Tab 重复
            callbacks.onThreeFingerSwipeLeft()
        }
    }
}

private fun flushMove(accumX: Float, accumY: Float, onMove: (Float, Float) -> Unit): Pair<Float, Float> {
    if (accumX == 0f && accumY == 0f) return accumX to accumY
    onMove(accumX, accumY)
    return 0f to 0f
}

private fun flushScroll(
    accumX: Float,
    accumY: Float,
    multiplier: Float,
    onScroll: (deltaY: Int, deltaX: Int) -> Unit,
): Pair<Float, Float> {
    var nx = accumX
    var ny = accumY
    val scrollY = (-ny * multiplier).toInt()
    val scrollX = (-nx * multiplier).toInt()
    if (scrollY != 0 || scrollX != 0) {
        onScroll(
            if (scrollY != 0) scrollY * WHEEL_DELTA / 8 else 0,
            if (scrollX != 0) scrollX * WHEEL_DELTA / 8 else 0,
        )
        if (scrollY != 0) ny = 0f
        if (scrollX != 0) nx = 0f
    }
    return nx to ny
}
