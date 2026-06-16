package com.yaokongqi.remote.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.GamepadButtons
import com.yaokongqi.remote.connection.ConnectionState
import com.yaokongqi.remote.model.GamepadControlId
import com.yaokongqi.remote.model.GamepadControlPlacement
import com.yaokongqi.remote.model.GamepadLayout
import com.yaokongqi.remote.model.GamepadLayouts
import com.yaokongqi.remote.model.GamepadSizeLimits
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.game.GamepadActionBindings
import com.yaokongqi.remote.ui.game.GamepadActionIconImage
import com.yaokongqi.remote.ui.game.GamepadActionIcons
import com.yaokongqi.remote.ui.game.resolveGamepadControlAlpha
import com.yaokongqi.remote.ui.game.GamepadInputEngine
import com.yaokongqi.remote.ui.game.HighRateTouch
import com.yaokongqi.remote.ui.game.HighRateTouch.forEachDeltaSample
import com.yaokongqi.remote.ui.game.HighRateTouch.forEachPositionSample
import com.yaokongqi.remote.ui.game.GamepadRadialWheelButton
import com.yaokongqi.remote.ui.game.GamepadTacticalButton
import com.yaokongqi.remote.ui.game.GamepadToggleButton
import com.yaokongqi.remote.ui.game.RadialWheelOverlay
import com.yaokongqi.remote.ui.game.RadialWheelSession
import com.yaokongqi.remote.ui.game.TacticalAimOverlay
import com.yaokongqi.remote.ui.theme.Primary
import com.yaokongqi.remote.ui.util.DisplayPerformance
import com.yaokongqi.remote.ui.util.findActivity
import com.yaokongqi.remote.ui.util.setGamepadLandscapeLock
import kotlin.math.max
import kotlin.math.roundToInt

private val GamepadCanvasColor = Color.Black

@Composable
fun GamepadScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    onOpenSettings: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    val isConnected = connectionInfo.state == ConnectionState.Connected
    val gamepadError by viewModel.gamepadError.collectAsState()
    val layoutEditRequest by viewModel.gamepadLayoutEditRequest.collectAsState()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var editMode by rememberSaveable { mutableStateOf(false) }
    var layout by remember(settings.gamepadLayout) {
        mutableStateOf(settings.gamepadLayout.normalized())
    }
    var selectedId by remember { mutableStateOf<GamepadControlId?>(null) }

    LaunchedEffect(layoutEditRequest) {
        if (layoutEditRequest > 0) {
            activity?.setGamepadLandscapeLock(true)
            editMode = true
        }
    }

    DisposableEffect(activity) {
        activity?.setGamepadLandscapeLock(true)
        activity?.let { DisplayPerformance.applyMaxPerformance(it) }
        onDispose {
            activity?.setGamepadLandscapeLock(false)
        }
    }

    LaunchedEffect(settings.gamepadLayout) {
        if (!editMode) {
            layout = settings.gamepadLayout.normalized()
        }
    }

    DisposableEffect(Unit) {
        viewModel.enterGamepadMode()
        onDispose { viewModel.exitGamepadMode() }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.syncGamepadModeIfConnected()
        }
    }

    LaunchedEffect(
        isConnected,
        editMode,
        settings.gamepadPollHz,
        settings.aimIdleDecay,
    ) {
        viewModel.gamepadEngine.setAimPollHz(settings.gamepadPollHz)
        if (!isConnected || editMode) {
            viewModel.pauseGamepadUpdates()
            return@LaunchedEffect
        }
        viewModel.runGamepadLoop(
            pollHz = settings.gamepadPollHz,
            aimIdleDecay = settings.aimIdleDecay,
        )
    }

    if (isConnected && gamepadError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearGamepadError,
            title = { Text("虚拟手柄不可用") },
            text = { Text(gamepadError ?: "") },
            confirmButton = {
                TextButton(onClick = viewModel::clearGamepadError) { Text("知道了") }
            },
        )
    }

    if (!isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GamepadCanvasColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "正在切换横屏…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val engine = viewModel.gamepadEngine
    var radialSession by remember { mutableStateOf<RadialWheelSession?>(null) }
    var tacticalAimActive by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadCanvasColor),
    ) {
        val containerW = maxWidth
        val containerH = maxHeight

        AimZone(
            modifier = Modifier.fillMaxSize(),
            controlAlpha = settings.gamepadControlAlpha,
            interactive = !editMode,
            showHint = editMode,
            onAimBegin = { engine.beginAim() },
            onAimDelta = { dx, dy, uptimeMillis ->
                engine.addAimDelta(
                    dx,
                    dy,
                    settings,
                    eventTimeNanos = uptimeMillis * 1_000_000L,
                )
                viewModel.publishAimDelta()
            },
            onAimRelease = {
                engine.releaseAim()
                viewModel.publishGamepadState()
            },
        )

        if (!editMode) {
            VirtualMoveStick(
                placement = layout.moveStickPlacement(),
                containerWidth = containerW,
                containerHeight = containerH,
                deadzone = settings.moveDeadzone,
                floatingStick = settings.gamepadFloatingStick,
                controlAlpha = settings.gamepadControlAlpha,
                hapticEnabled = settings.hapticFeedback,
                interactive = true,
                onStick = { lx, ly ->
                    engine.setMoveStick(lx, ly, settings.moveSensitivity)
                    viewModel.publishGamepadState()
                },
                onSprintChange = { sprint ->
                    engine.setButton(GamepadButtons.LS, sprint)
                    viewModel.publishGamepadState()
                },
            )
        }

        val sortedControls = layout.layoutControls().let { controls ->
            val selected = selectedId?.let { id -> controls.find { it.id == id } }
            if (selected != null) {
                controls.filter { it.id != selected.id } + selected
            } else {
                controls
            }
        }
        sortedControls.forEach { placement ->
            if (!editMode && placement.id == GamepadControlId.MOVE_STICK) return@forEach
            PlacedGamepadControl(
                placement = placement,
                containerWidth = containerW,
                containerHeight = containerH,
                editMode = editMode,
                selected = selectedId == placement.id,
                onSelect = { selectedId = placement.id },
                onMove = { dx, dy ->
                    layout = layout.update(placement.id) {
                        it.copy(
                            centerX = (it.centerX + dx).coerceIn(0.04f, 0.96f),
                            centerY = (it.centerY + dy).coerceIn(0.06f, 0.94f),
                        )
                    }
                },
            ) { childModifier ->
                if (placement.id == GamepadControlId.MOVE_STICK) {
                    MoveStickVisual(
                        ringSizeDp = placement.sizeDp.dp,
                        controlAlpha = placement.effectiveAlpha(settings.gamepadControlAlpha),
                        active = false,
                        sprintActive = false,
                        modifier = childModifier,
                    )
                } else when (placement.id) {
                    GamepadControlId.HEAL, GamepadControlId.THROW -> {
                        GamepadRadialWheelButton(
                            id = placement.id,
                            engine = engine,
                            modifier = childModifier,
                            globalControlAlpha = settings.gamepadControlAlpha,
                            placementOpacity = placement.opacity,
                            hapticEnabled = settings.hapticFeedback,
                            interactive = !editMode,
                            onWheelChange = { session -> radialSession = session },
                            onPublishState = viewModel::publishGamepadState,
                        )
                    }
                    GamepadControlId.ADS, GamepadControlId.LT -> {
                        GamepadToggleButton(
                            id = placement.id,
                            modifier = childModifier,
                            globalControlAlpha = settings.gamepadControlAlpha,
                            placementOpacity = placement.opacity,
                            hapticEnabled = settings.hapticFeedback,
                            interactive = !editMode,
                            onToggle = {
                                engine.toggleLeftTrigger()
                                viewModel.publishGamepadState()
                            },
                        )
                    }
                    GamepadControlId.TACTICAL -> {
                        GamepadTacticalButton(
                            id = placement.id,
                            modifier = childModifier,
                            globalControlAlpha = settings.gamepadControlAlpha,
                            placementOpacity = placement.opacity,
                            hapticEnabled = settings.hapticFeedback,
                            interactive = !editMode,
                            engine = engine,
                            onAimModeChange = { active -> tacticalAimActive = active },
                            onPublishState = viewModel::publishGamepadState,
                            onAimDelta = { dx, dy, uptimeMillis ->
                                engine.addAimDelta(
                                    dx,
                                    dy,
                                    settings,
                                    eventTimeNanos = uptimeMillis * 1_000_000L,
                                )
                                viewModel.publishAimDelta()
                            },
                        )
                    }
                    else -> {
                        GamepadActionButton(
                            id = placement.id,
                            modifier = childModifier,
                            accent = placement.accent,
                            globalControlAlpha = settings.gamepadControlAlpha,
                            placementOpacity = placement.opacity,
                            hapticEnabled = settings.hapticFeedback,
                            interactive = !editMode,
                            onPressChange = { pressed ->
                                handleGamepadButton(engine, placement.id, pressed)
                                viewModel.publishGamepadState()
                            },
                        )
                    }
                }
            }
        }

        radialSession?.let { session ->
            RadialWheelOverlay(session = session)
        }

        TacticalAimOverlay(
            active = tacticalAimActive,
            onAimDelta = { dx, dy, uptimeMillis ->
                engine.addAimDelta(
                    dx,
                    dy,
                    settings,
                    eventTimeNanos = uptimeMillis * 1_000_000L,
                )
                viewModel.publishAimDelta()
            },
        )

        GamepadTopBar(
            editMode = editMode,
            onEnterEdit = { editMode = true },
            onRestoreDefault = {
                layout = GamepadLayouts.default()
                selectedId = null
            },
            onFinishEdit = {
                viewModel.saveGamepadLayout(layout.normalized())
                editMode = false
                selectedId = null
            },
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp),
        )

        if (!isConnected && !editMode) {
            Text(
                "未连接 PC · 可编辑布局与设置",
                style = MaterialTheme.typography.labelSmall,
                color = gamepadOverlayTextColor(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
            )
        }

        if (isConnected && !editMode) {
            GamepadRttIndicator(
                latencyMs = connectionInfo.latencyMs,
                packetLossPercent = connectionInfo.packetLossPercent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }

        if (editMode) {
            Text(
                "拖动按键/轮盘调位置；左下角调节大小与透明度",
                style = MaterialTheme.typography.labelSmall,
                color = gamepadOverlayTextColor(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
            )
            selectedId?.let { id ->
                LayoutButtonEditor(
                    placement = layout.placement(id),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp),
                    onUpdate = { updated ->
                        layout = layout.update(id) { updated }
                    },
                )
            }
        }
    }
}

@Composable
private fun gamepadOverlayTextColor(): Color = Color.White.copy(alpha = 0.72f)

/** 游戏模式顶部网络延迟，低对比度，仅在连接时显示 */
@Composable
private fun GamepadRttIndicator(
    latencyMs: Int?,
    packetLossPercent: Int,
    modifier: Modifier = Modifier,
) {
    val text = buildString {
        append(latencyMs?.toString() ?: "--")
        append("ms")
        if (packetLossPercent > 0) {
            append(" · ")
            append(packetLossPercent)
            append('%')
        }
    }
    val color = when {
        packetLossPercent > 5 -> Color(0xFFFFB74D).copy(alpha = 0.52f)
        latencyMs != null && latencyMs > 50 -> Color(0xFFFFB74D).copy(alpha = 0.45f)
        else -> Color.White.copy(alpha = 0.36f)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.2.sp,
        ),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun GamepadTopBar(
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onRestoreDefault: () -> Unit,
    onFinishEdit: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 2.dp,
    ) {
        val textColors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editMode) {
                TextButton(
                    onClick = onRestoreDefault,
                    colors = textColors,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("恢复默认", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onFinishEdit,
                    colors = textColors,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("完成编辑", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(
                    onClick = onEnterEdit,
                    colors = textColors,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("编辑布局", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PlacedGamepadControl(
    placement: GamepadControlPlacement,
    containerWidth: Dp,
    containerHeight: Dp,
    editMode: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val diameterDp = placement.sizeDp.dp
    val hitDiameterDp = when {
        editMode -> max(placement.sizeDp, 72f).dp
        placement.id == GamepadControlId.ADS || placement.id == GamepadControlId.LT ->
            max(placement.sizeDp, 68f).dp
        else -> max(placement.sizeDp, 56f).dp
    }
    val xOff = (placement.centerX * containerWidth.value - hitDiameterDp.value / 2f)
        .coerceIn(0f, (containerWidth - hitDiameterDp).value.coerceAtLeast(0f).toFloat())
    val yOff = (placement.centerY * containerHeight.value - hitDiameterDp.value / 2f)
        .coerceIn(0f, (containerHeight - hitDiameterDp).value.coerceAtLeast(0f).toFloat())

    val density = LocalDensity.current
    val containerWidthPx = with(density) { containerWidth.toPx() }
    val containerHeightPx = with(density) { containerHeight.toPx() }

    Box(
        modifier = Modifier
            .offset(x = xOff.dp, y = yOff.dp)
            .size(hitDiameterDp)
            .clip(CircleShape)
            .then(
                if (editMode) {
                    // 仅用 id 作为 key，避免拖动时 center 变化导致手势被中断
                    Modifier.pointerInput(placement.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMove(
                                    dragAmount.x / containerWidthPx,
                                    dragAmount.y / containerHeightPx,
                                )
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(diameterDp)
                .clip(CircleShape)
                .then(
                    if (editMode) {
                        Modifier.border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            content(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun LayoutButtonEditor(
    placement: GamepadControlPlacement,
    modifier: Modifier = Modifier,
    onUpdate: (GamepadControlPlacement) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    Surface(
        modifier = modifier.widthIn(max = 248.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "已选：${GamepadLayouts.label(placement.id)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            LayoutSliderRow(
                label = if (placement.id == GamepadControlId.MOVE_STICK) "轮盘" else "大小",
                valueText = "${GamepadSizeLimits.buttonSizePercent(placement.sizeDp)}%",
                value = placement.sizeDp,
                valueRange = if (placement.id == GamepadControlId.MOVE_STICK) {
                    GamepadSizeLimits.MOVE_STICK_MIN_DP..GamepadSizeLimits.MOVE_STICK_MAX_DP
                } else {
                    GamepadSizeLimits.BUTTON_MIN_DP..GamepadSizeLimits.BUTTON_MAX_DP
                },
                onValueChange = {
                    onUpdate(
                        placement.copy(
                            sizeDp = GamepadSizeLimits.clampButtonSize(placement.id, it),
                        ),
                    )
                },
                textColor = subTextColor,
                valueTextWidth = 40.dp,
            )
            LayoutSliderRow(
                label = "透明",
                valueText = "${(placement.opacity * 100f).roundToInt()}%",
                value = placement.opacity,
                valueRange = 0.15f..1f,
                onValueChange = { onUpdate(placement.copy(opacity = it)) },
                textColor = subTextColor,
            )
        }
    }
}

@Composable
private fun LayoutSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    textColor: Color,
    valueTextWidth: Dp = 36.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.width(32.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.width(valueTextWidth),
            textAlign = TextAlign.End,
        )
    }
}

private const val MoveStickMaxThrowRatio = 0.72f
private const val SprintTriggerUpNorm = 0.72f
private const val SprintMaxLateralNorm = 0.62f

@Composable
private fun MoveStickVisual(
    ringSizeDp: Dp,
    controlAlpha: Float,
    active: Boolean,
    sprintActive: Boolean,
    knobOffsetPx: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val alpha = controlAlpha.coerceIn(0.15f, 0.95f)
    val ringRadiusDp = ringSizeDp / 2f
    val knobSizeDp = (ringSizeDp.value * 0.36f).coerceIn(28f, 52f).dp
    val knobOffsetDp = with(density) {
        DpOffset(knobOffsetPx.x.toDp(), knobOffsetPx.y.toDp())
    }

    val ringAlpha by animateFloatAsState(
        targetValue = when {
            active -> (alpha + 0.22f).coerceAtMost(0.95f)
            sprintActive -> (alpha + 0.15f).coerceAtMost(0.88f)
            else -> alpha * 0.62f
        },
        animationSpec = tween(80),
        label = "move_ring_alpha",
    )
    val knobAlpha by animateFloatAsState(
        targetValue = when {
            active -> (alpha + 0.42f).coerceAtMost(1f)
            sprintActive -> alpha * 0.9f
            else -> alpha * 0.78f
        },
        animationSpec = tween(80),
        label = "move_knob_alpha",
    )
    val ringBorder = when {
        sprintActive -> Primary.copy(alpha = 0.92f)
        active -> Primary.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val ringFill = MaterialTheme.colorScheme.surface.copy(
        alpha = if (sprintActive) 0.14f else 0.08f,
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(ringSizeDp)
                .alpha(ringAlpha)
                .background(ringFill, CircleShape)
                .border(1.5.dp, ringBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = when {
                    sprintActive -> Primary.copy(alpha = 0.95f)
                    active -> Primary.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (ringRadiusDp.value * 0.18f).dp)
                    .size((ringSizeDp.value * 0.22f).coerceIn(14f, 24f).dp),
            )
            Box(
                modifier = Modifier
                    .offset(knobOffsetDp.x, knobOffsetDp.y)
                    .size(knobSizeDp)
                    .alpha(knobAlpha)
                    .scale(if (active) 1f else 0.96f)
                    .background(
                        if (sprintActive) Primary.copy(alpha = 0.72f)
                        else Primary.copy(alpha = 0.52f),
                        CircleShape,
                    )
                    .border(
                        width = if (active || sprintActive) 2.dp else 1.5.dp,
                        color = Color.White.copy(alpha = if (active) 0.55f else 0.32f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun VirtualMoveStick(
    placement: GamepadControlPlacement,
    containerWidth: Dp,
    containerHeight: Dp,
    deadzone: Float,
    floatingStick: Boolean,
    controlAlpha: Float,
    hapticEnabled: Boolean,
    interactive: Boolean,
    onStick: (Int, Int) -> Unit,
    onSprintChange: (Boolean) -> Unit = {},
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val alpha = controlAlpha.coerceIn(0.15f, 0.95f)

    val zoneWidthPx = with(density) { containerWidth.toPx() }
    val zoneHeightPx = with(density) { containerHeight.toPx() }
    val stickRadiusPx = with(density) { (placement.sizeDp.dp / 2f).toPx() }
    val maxThrowPx = stickRadiusPx * MoveStickMaxThrowRatio
    val leftBoundaryPx = zoneWidthPx * 0.5f

    val thumbBase = remember(zoneWidthPx, zoneHeightPx, placement.centerX, placement.centerY) {
        Offset(
            zoneWidthPx * placement.centerX,
            zoneHeightPx * placement.centerY,
        )
    }

    var active by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var sprinting by remember { mutableStateOf(false) }

    fun setSprinting(value: Boolean) {
        if (sprinting == value) return
        sprinting = value
        onSprintChange(value)
    }

    fun clampAnchor(pos: Offset): Offset = Offset(
        pos.x.coerceIn(stickRadiusPx, leftBoundaryPx - stickRadiusPx),
        pos.y.coerceIn(stickRadiusPx, zoneHeightPx - stickRadiusPx),
    )

    fun stickBase(): Offset = if (floatingStick && active) anchor else thumbBase

    fun isMoveTouch(offset: Offset): Boolean {
        if (offset.x > leftBoundaryPx) return false
        if (floatingStick) return true
        val base = thumbBase
        val dist = kotlin.math.hypot(
            (offset.x - base.x).toDouble(),
            (offset.y - base.y).toDouble(),
        ).toFloat()
        return dist <= stickRadiusPx * 1.35f
    }

    fun emitStick(clamped: Offset, sprint: Boolean) {
        if (sprint) {
            val (lx, ly) = GamepadInputEngine.sprintMoveStick(clamped.x, maxThrowPx)
            onStick(lx, ly)
        } else {
            val (lx, ly) = GamepadInputEngine.offsetToStickForMove(
                clamped.x,
                clamped.y,
                maxThrowPx,
                if (floatingStick) deadzone * 0.65f else deadzone,
            )
            onStick(lx, ly)
        }
    }

    fun updateFromTouch(touch: Offset) {
        var base = stickBase()
        var delta = touch - base

        if (floatingStick && active) {
            val mag = kotlin.math.hypot(delta.x.toDouble(), delta.y.toDouble()).toFloat()
            if (mag > maxThrowPx) {
                val clampedDelta = clampToRadius(delta, maxThrowPx)
                val excess = Offset(delta.x - clampedDelta.x, delta.y - clampedDelta.y)
                anchor = clampAnchor(anchor + excess)
                base = stickBase()
                delta = touch - base
            }
        }

        val clamped = clampToRadius(delta, maxThrowPx)
        knobOffset = clamped

        val upNorm = if (maxThrowPx > 0f) (-clamped.y / maxThrowPx) else 0f
        val lateralNorm = if (maxThrowPx > 0f) kotlin.math.abs(clamped.x) / maxThrowPx else 0f
        val shouldSprint = upNorm >= SprintTriggerUpNorm && lateralNorm <= SprintMaxLateralNorm

        if (shouldSprint && !sprinting) {
            setSprinting(true)
            if (hapticEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            }
        } else if (!shouldSprint && sprinting) {
            setSprinting(false)
        }

        emitStick(clamped, shouldSprint)
    }

    fun releaseStick() {
        active = false
        knobOffset = Offset.Zero
        setSprinting(false)
        onStick(0, 0)
    }

    val showStick = !floatingStick || active
    val base = stickBase()
    val displayKnob = knobOffset
    val xOff = (base.x - stickRadiusPx).coerceAtLeast(0f)
    val yOff = (base.y - stickRadiusPx).coerceAtLeast(0f)
    val ringSizeDp = with(density) { (stickRadiusPx * 2f).toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (interactive) {
            // 仅左半屏接收移动轮盘手势，避免挡住右半屏瞄准
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .pointerInput(floatingStick, deadzone, stickRadiusPx, placement) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (!isMoveTouch(offset)) return@detectDragGestures
                                active = true
                                anchor = clampAnchor(offset)
                                if (hapticEnabled) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    )
                                }
                                updateFromTouch(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                change.forEachPositionSample { pos ->
                                    if (!isMoveTouch(pos)) return@forEachPositionSample
                                    updateFromTouch(pos)
                                }
                            },
                            onDragEnd = { releaseStick() },
                            onDragCancel = { releaseStick() },
                        )
                    },
            )
        }
        if (showStick) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(xOff.roundToInt(), yOff.roundToInt()) }
                    .size(ringSizeDp)
                    .alpha(if (floatingStick && !active) 0f else 1f),
            ) {
                MoveStickVisual(
                    ringSizeDp = ringSizeDp,
                    controlAlpha = alpha,
                    active = active,
                    sprintActive = sprinting,
                    knobOffsetPx = displayKnob,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AimZone(
    modifier: Modifier = Modifier,
    controlAlpha: Float,
    interactive: Boolean,
    showHint: Boolean = false,
    onAimBegin: () -> Unit,
    onAimDelta: (dx: Float, dy: Float, uptimeMillis: Long) -> Unit,
    onAimRelease: () -> Unit,
) {
    val alpha = controlAlpha.coerceIn(0.15f, 0.95f)
    var aiming by remember { mutableStateOf(false) }

    val zoneAlpha by animateFloatAsState(
        targetValue = when {
            !showHint -> 0f
            aiming -> (alpha + 0.22f).coerceAtMost(0.75f)
            else -> alpha * 0.45f
        },
        animationSpec = tween(60),
        label = "aim_zone_alpha",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = when {
            !showHint -> 0f
            aiming -> 0.75f
            else -> 0.22f
        },
        animationSpec = tween(60),
        label = "aim_border_alpha",
    )

    Box(modifier = modifier) {
        if (interactive) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            aiming = true
                            onAimBegin()
                            drag(down.id) { change ->
                                change.forEachDeltaSample { dx, dy, uptimeMillis ->
                                    onAimDelta(dx, dy, uptimeMillis)
                                }
                                change.consume()
                            }
                            aiming = false
                            onAimRelease()
                        }
                    },
            )
        }
        if (showHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .alpha(zoneAlpha)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = if (aiming) 0.32f else 0.12f,
                        ),
                        RoundedCornerShape(16.dp),
                    )
                    .border(
                        width = if (aiming) 2.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha),
                        shape = RoundedCornerShape(16.dp),
                    ),
            )
        }
    }
}

@Composable
private fun GamepadActionButton(
    id: GamepadControlId,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    globalControlAlpha: Float = 0.5f,
    placementOpacity: Float = 1f,
    hapticEnabled: Boolean = true,
    interactive: Boolean = true,
    onPressChange: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val alpha = resolveGamepadControlAlpha(globalControlAlpha, placementOpacity)
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(80),
        label = "btn_scale",
    )
    val displayAlpha by animateFloatAsState(
        targetValue = if (pressed) (alpha + 0.40f).coerceAtMost(1f) else alpha,
        animationSpec = tween(80),
        label = "btn_alpha",
    )

    val customDrawable = GamepadActionIcons.drawableRes(id)
    val hasCustomIcon = customDrawable != null

    if (hasCustomIcon) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .scale(scale)
                .clip(CircleShape)
                .then(
                    if (interactive) {
                        Modifier.pointerInput(id) {
                            detectTapGestures(
                                onPress = {
                                    pressed = true
                                    if (hapticEnabled) {
                                        view.performHapticFeedback(
                                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                        )
                                    }
                                    onPressChange(true)
                                    tryAwaitRelease()
                                    pressed = false
                                    onPressChange(false)
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            GamepadActionIconImage(
                drawableRes = customDrawable,
                contentDescription = GamepadLayouts.label(id),
                displayAlpha = displayAlpha,
            )
        }
        return
    }

    val bg = when {
        pressed && accent -> Primary.copy(alpha = displayAlpha)
        pressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = displayAlpha)
        accent -> Primary.copy(alpha = alpha * 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.85f)
    }
    val borderColor = when {
        pressed && accent -> Color.White.copy(alpha = 0.85f)
        pressed -> Primary.copy(alpha = 0.9f)
        accent -> Primary.copy(alpha = alpha * 0.75f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = alpha * 0.55f)
    }
    val iconTint = when {
        pressed && accent -> Color.White
        pressed -> MaterialTheme.colorScheme.onPrimaryContainer
        accent -> Primary.copy(alpha = (alpha + 0.25f).coerceAtMost(1f))
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = (alpha + 0.35f).coerceAtMost(1f))
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (interactive) {
                    Modifier.pointerInput(id) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                if (hapticEnabled) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    )
                                }
                                onPressChange(true)
                                tryAwaitRelease()
                                pressed = false
                                onPressChange(false)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        shape = CircleShape,
        color = bg,
        border = androidx.compose.foundation.BorderStroke(
            width = if (pressed) 2.5.dp else 1.5.dp,
            color = borderColor,
        ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = GamepadActionIcons.fallbackIcon(id),
                contentDescription = GamepadLayouts.label(id),
                tint = iconTint,
                modifier = Modifier.fillMaxSize(0.52f),
            )
        }
    }
}

private fun handleGamepadButton(
    engine: GamepadInputEngine,
    id: GamepadControlId,
    pressed: Boolean,
) {
    GamepadActionBindings.apply(engine, id, pressed)
}

private fun clampToRadius(delta: Offset, maxRadius: Float): Offset {
    val mag = kotlin.math.hypot(delta.x.toDouble(), delta.y.toDouble()).toFloat()
    if (mag <= maxRadius || mag == 0f) return delta
    val scale = maxRadius / mag
    return Offset(delta.x * scale, delta.y * scale)
}
