package com.yaokongqi.remote.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SWIPE_THRESHOLD_PX = 120f
private const val CONFIRM_WINDOW_MS = 2500L
private const val EDGE_SWIPE_MAX_X = 56f

@Composable
fun ExitConfirmLayer(
    enabled: Boolean,
    onExit: () -> Unit,
    /** 游戏模式：监听左右边缘滑动手势，降低全面屏返回误触 */
    gameMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    var swipeStep by remember { mutableIntStateOf(0) }
    var showHint by remember { mutableStateOf(false) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun scheduleReset() {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(CONFIRM_WINDOW_MS)
            swipeStep = 0
            showHint = false
        }
    }

    fun registerSwipe() {
        if (swipeStep == 0) {
            swipeStep = 1
            showHint = true
            scheduleReset()
        } else {
            resetJob?.cancel()
            swipeStep = 0
            showHint = false
            onExit()
        }
    }

    BackHandler { registerSwipe() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edgeMax = if (gameMode) 72f else EDGE_SWIPE_MAX_X
                    val onLeftEdge = down.position.x <= edgeMax
                    val onRightEdge = gameMode && down.position.x >= size.width - edgeMax
                    if (!onLeftEdge && !onRightEdge) return@awaitEachGesture
                    var totalDrag = 0f
                    do {
                        val event = awaitPointerEvent()
                        val drag = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = drag.position - drag.previousPosition
                        totalDrag += if (onRightEdge) -delta.x else delta.x
                    } while (event.changes.any { it.pressed })
                    if (abs(totalDrag) >= SWIPE_THRESHOLD_PX) {
                        registerSwipe()
                    }
                }
            },
    ) {
        content()
        if (showHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (gameMode) "再滑动一次退出游戏" else "再滑动一次退出应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
