package com.yaokongqi.remote.ui.game

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.yaokongqi.remote.ui.game.HighRateTouch.forEachDeltaSample
import com.yaokongqi.remote.ui.game.HighRateTouch.forEachPositionSample
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yaokongqi.remote.model.GamepadButtons
import com.yaokongqi.remote.model.GamepadControlId
import com.yaokongqi.remote.model.GamepadLayouts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadialWheelOption(
    val label: String,
    val buttonMask: Int,
)

object GamepadRadialWheels {
    /** 顺序：上 / 右 / 下 / 左 */
    val heal = listOf(
        RadialWheelOption("注射器", GamepadButtons.UP),
        RadialWheelOption("凤凰", GamepadButtons.RIGHT),
        RadialWheelOption("医疗包", GamepadButtons.DOWN),
        RadialWheelOption("护盾", GamepadButtons.LEFT),
    )

    val throwables = listOf(
        RadialWheelOption("电弧", GamepadButtons.UP),
        RadialWheelOption("破片", GamepadButtons.RIGHT),
        RadialWheelOption("燃烧", GamepadButtons.DOWN),
        RadialWheelOption("雷火", GamepadButtons.LEFT),
    )

    fun defaultIndex(id: GamepadControlId): Int = when (id) {
        GamepadControlId.HEAL -> 0
        GamepadControlId.THROW -> 1
        else -> 0
    }

    fun options(id: GamepadControlId): List<RadialWheelOption> = when (id) {
        GamepadControlId.HEAL -> heal
        GamepadControlId.THROW -> throwables
        else -> emptyList()
    }

    fun quickUseMask(id: GamepadControlId): Int = when (id) {
        GamepadControlId.HEAL -> GamepadButtons.UP
        GamepadControlId.THROW -> GamepadButtons.RIGHT
        else -> GamepadButtons.UP
    }
}

data class RadialWheelSession(
    val anchorPx: Offset,
    val options: List<RadialWheelOption>,
    val defaultIndex: Int,
    val selectedIndex: Int = defaultIndex,
)

private const val LONG_PRESS_MS = 280L
private const val PULSE_MS = 90L
private val WHEEL_RADIUS_DP = 72.dp

fun sectorIndexForDrag(options: List<RadialWheelOption>, drag: Offset, minDragPx: Float): Int {
    if (drag.getDistance() < minDragPx) return -1
    val angleDeg = Math.toDegrees(atan2(drag.y.toDouble(), drag.x.toDouble())).toFloat()
    val sector = when {
        angleDeg >= -45f && angleDeg < 45f -> 1
        angleDeg >= 45f && angleDeg < 135f -> 2
        angleDeg >= -135f && angleDeg < -45f -> 0
        else -> 3
    }
    return sector.coerceIn(0, options.lastIndex)
}

suspend fun pulseGamepadButton(
    engine: GamepadInputEngine,
    mask: Int,
    onPublishState: () -> Unit = {},
) {
    engine.setButton(mask, true)
    onPublishState()
    delay(PULSE_MS)
    engine.setButton(mask, false)
    onPublishState()
}

@Composable
fun GamepadRadialWheelButton(
    id: GamepadControlId,
    engine: GamepadInputEngine,
    modifier: Modifier = Modifier,
    globalControlAlpha: Float,
    placementOpacity: Float,
    hapticEnabled: Boolean,
    interactive: Boolean,
    onWheelChange: (RadialWheelSession?) -> Unit,
    onPublishState: () -> Unit = {},
) {
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val minDragPx = with(density) { 20.dp.toPx() }
    val alpha = resolveGamepadControlAlpha(globalControlAlpha, placementOpacity)
    var pressed by remember { mutableStateOf(false) }
    val drawable = GamepadActionIcons.drawableRes(id)
    val quickMask = GamepadRadialWheels.quickUseMask(id)
    val options = GamepadRadialWheels.options(id)
    val defaultIndex = GamepadRadialWheels.defaultIndex(id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(
                if (interactive) {
                    Modifier.pointerInput(id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            var wheelOpened = false
                            var selectedIndex = defaultIndex
                            var totalDrag = Offset.Zero
                            val slop = viewConfiguration.touchSlop
                            val downTimeMs = SystemClock.uptimeMillis()
                            val anchorPx = down.position

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (
                                    !wheelOpened &&
                                    SystemClock.uptimeMillis() - downTimeMs >= LONG_PRESS_MS
                                ) {
                                    wheelOpened = true
                                    if (hapticEnabled) {
                                        view.performHapticFeedback(
                                            android.view.HapticFeedbackConstants.LONG_PRESS,
                                        )
                                    }
                                    onWheelChange(
                                        RadialWheelSession(
                                            anchorPx = anchorPx,
                                            options = options,
                                            defaultIndex = defaultIndex,
                                            selectedIndex = selectedIndex,
                                        ),
                                    )
                                }

                                change.forEachDeltaSample { dx, dy, _ ->
                                    totalDrag += Offset(dx, dy)
                                    if (wheelOpened) {
                                        val idx = sectorIndexForDrag(options, totalDrag, minDragPx)
                                        if (idx >= 0 && idx != selectedIndex) {
                                            selectedIndex = idx
                                            onWheelChange(
                                                RadialWheelSession(
                                                    anchorPx = anchorPx,
                                                    options = options,
                                                    defaultIndex = defaultIndex,
                                                    selectedIndex = selectedIndex,
                                                ),
                                            )
                                        }
                                    }
                                }
                                change.consume()

                                if (!change.pressed) break
                            }

                            pressed = false

                            if (wheelOpened) {
                                val mask = options.getOrElse(selectedIndex) { options[defaultIndex] }.buttonMask
                                scope.launch { pulseGamepadButton(engine, mask, onPublishState) }
                                onWheelChange(null)
                            } else if (totalDrag.getDistance() < slop) {
                                if (hapticEnabled) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    )
                                }
                                scope.launch { pulseGamepadButton(engine, quickMask, onPublishState) }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            GamepadActionIconImage(
                drawableRes = drawable,
                contentDescription = GamepadLayouts.label(id),
                displayAlpha = gamepadIconDisplayAlpha(alpha, pressed),
            )
        }
    }
}

@Composable
fun RadialWheelOverlay(
    session: RadialWheelSession,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { WHEEL_RADIUS_DP.toPx() }
    val selectedIndex = session.selectedIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(50f),
    ) {
        val wheelSize = WHEEL_RADIUS_DP * 2.4f
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (session.anchorPx.x - with(density) { wheelSize.toPx() } / 2f).roundToInt(),
                        (session.anchorPx.y - with(density) { wheelSize.toPx() } / 2f).roundToInt(),
                    )
                }
                .size(wheelSize),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = radiusPx, center = Offset(cx, cy))
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = radiusPx,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f),
                )
            }
            session.options.forEachIndexed { index, option ->
                val angleRad = Math.toRadians((index * 90 - 90).toDouble())
                val labelRadius = with(density) { 46.dp.toPx() }
                val lx = cos(angleRad).toFloat() * labelRadius
                val ly = sin(angleRad).toFloat() * labelRadius
                Text(
                    text = option.label,
                    color = if (index == selectedIndex) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = if (index == selectedIndex) 12.sp else 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(lx.roundToInt(), ly.roundToInt()) },
                )
            }
        }
    }
}

@Composable
fun GamepadTacticalButton(
    id: GamepadControlId,
    modifier: Modifier = Modifier,
    globalControlAlpha: Float,
    placementOpacity: Float,
    hapticEnabled: Boolean,
    interactive: Boolean,
    engine: GamepadInputEngine,
    onAimDelta: (dx: Float, dy: Float, uptimeMillis: Long) -> Unit,
    onAimModeChange: (Boolean) -> Unit = {},
    onPublishState: () -> Unit = {},
) {
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val alpha = resolveGamepadControlAlpha(globalControlAlpha, placementOpacity)
    var pressed by remember { mutableStateOf(false) }
    val drawable = GamepadActionIcons.drawableRes(id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(
                if (interactive) {
                    Modifier.pointerInput(id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            var aimMode = false
                            var totalDrag = Offset.Zero
                            val slop = viewConfiguration.touchSlop
                            val downTimeMs = SystemClock.uptimeMillis()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (
                                    !aimMode &&
                                    SystemClock.uptimeMillis() - downTimeMs >= LONG_PRESS_MS
                                ) {
                                    aimMode = true
                                    engine.setTacticalBumper(true)
                                    engine.beginAim()
                                    onPublishState()
                                    onAimModeChange(true)
                                    if (hapticEnabled) {
                                        view.performHapticFeedback(
                                            android.view.HapticFeedbackConstants.LONG_PRESS,
                                        )
                                    }
                                }

                                change.forEachDeltaSample { dx, dy, uptimeMillis ->
                                    totalDrag += Offset(dx, dy)
                                    if (aimMode) {
                                        onAimDelta(dx, dy, uptimeMillis)
                                    }
                                }
                                change.consume()

                                if (!change.pressed) break
                            }

                            pressed = false

                            if (aimMode) {
                                engine.releaseAim()
                                engine.setTacticalBumper(false)
                                onPublishState()
                                onAimModeChange(false)
                            } else if (totalDrag.getDistance() < slop) {
                                if (hapticEnabled) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    )
                                }
                                scope.launch {
                                    engine.setTacticalBumper(true)
                                    onPublishState()
                                    delay(PULSE_MS)
                                    engine.setTacticalBumper(false)
                                    onPublishState()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            GamepadActionIconImage(
                drawableRes = drawable,
                contentDescription = GamepadLayouts.label(id),
                displayAlpha = gamepadIconDisplayAlpha(alpha, pressed),
            )
        }
    }
}

@Composable
fun GamepadToggleButton(
    id: GamepadControlId,
    modifier: Modifier = Modifier,
    globalControlAlpha: Float,
    placementOpacity: Float,
    hapticEnabled: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit,
) {
    val view = LocalView.current
    val alpha = resolveGamepadControlAlpha(globalControlAlpha, placementOpacity)
    var pressed by remember { mutableStateOf(false) }
    val drawable = GamepadActionIcons.drawableRes(id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(
                if (interactive) {
                    Modifier.pointerInput(id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            if (hapticEnabled) {
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                )
                            }
                            onToggle()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.pressed } ?: break
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            pressed = false
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            GamepadActionIconImage(
                drawableRes = drawable,
                contentDescription = GamepadLayouts.label(id),
                displayAlpha = gamepadIconDisplayAlpha(alpha, pressed),
            )
        }
    }
}

@Composable
fun TacticalAimOverlay(
    active: Boolean,
    onAimDelta: (dx: Float, dy: Float, uptimeMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(45f)
            .pointerInput(active) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.forEachDeltaSample { dx, dy, uptimeMillis ->
                            totalDrag += Offset(dx, dy)
                            onAimDelta(dx, dy, uptimeMillis)
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                }
            },
    )
}
