package com.yaokongqi.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.ButtonAction
import com.yaokongqi.remote.model.ButtonLayout
import com.yaokongqi.remote.model.FullKeyboardLayout
import com.yaokongqi.remote.model.GridLayoutHelper
import com.yaokongqi.remote.model.KeyboardKey
import com.yaokongqi.remote.model.LayoutMode
import com.yaokongqi.remote.model.RemoteButton
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.gesture.TrackpadCallbacks
import com.yaokongqi.remote.ui.gesture.laptopTrackpadGestures
import com.yaokongqi.remote.ui.gesture.onTwoFingerPinchOut
import com.yaokongqi.remote.ui.theme.Primary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectedPadScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    onPinchOutToMinimal: () -> Unit,
) {
    val layout by viewModel.buttonLayout.collectAsState()
    val presets by viewModel.layoutPresets.collectAsState()
    val textInputVisible by viewModel.textInputVisible.collectAsState()
    val volumeConfirm by viewModel.volumeConfirmPending.collectAsState()
    val volumeCount by viewModel.volumeUpPressCount.collectAsState()
    val inputSessionKey by viewModel.inputSessionKey.collectAsState()
    val configuration = LocalConfiguration.current
    val landscapeSplit = settings.landscapeSplitLayout &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val presetSwipeEnabled = presets.presets.size > 1

    if (volumeConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissVolumeConfirm,
            title = { Text("音量已加至 $volumeCount 次") },
            text = { Text("继续增加音量？请确认，防止误触。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmVolumeUp) { Text("继续增加") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissVolumeConfirm) { Text("取消") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            textInputVisible && !landscapeSplit -> {
                TextInputPanel(
                    modifier = Modifier.fillMaxSize(),
                    onSend = { text ->
                        viewModel.sendText(text)
                        viewModel.hideTextInput()
                    },
                    onDismiss = viewModel::hideTextInput,
                )
            }
            landscapeSplit -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .padding(start = 12.dp, end = 6.dp, top = 0.dp, bottom = 6.dp)
                            .onTwoFingerPinchOut(
                                enabled = settings.pinchToMinimalEnabled,
                                onPinchOut = onPinchOutToMinimal,
                            ),
                    ) {
                        PadHeader(presets = presets, visible = false)
                        PadArea(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            layout = layout,
                            settings = settings,
                            onButton = viewModel::sendButton,
                            onKeyboardKey = viewModel::sendKeyboardKey,
                        )
                    }
                    PresetSwipeGap(
                        modifier = Modifier
                            .weight(0.06f)
                            .fillMaxHeight(),
                        enabled = presetSwipeEnabled,
                        vertical = true,
                        onSwipeForward = viewModel::switchPresetNext,
                        onSwipeBack = viewModel::switchPresetPrevious,
                    )
                    key(inputSessionKey) {
                        TouchpadArea(
                            modifier = Modifier
                                .weight(0.48f)
                                .fillMaxHeight()
                                .padding(end = 8.dp, bottom = 6.dp),
                            settings = settings,
                            inputSessionKey = inputSessionKey,
                            onMove = viewModel::sendMouseMove,
                            onSingleClick = viewModel::sendMouseLeftClick,
                            onDoubleClick = viewModel::sendMouseDoubleClick,
                            onRightClick = viewModel::sendMouseRightClick,
                            onScroll = { dy, dx -> viewModel.sendMouseScroll(dy, dx) },
                            onThreeFingerSwipeLeft = viewModel::sendAltShiftTab,
                            onThreeFingerSwipeRight = viewModel::sendAltTab,
                            onThreeFingerSwipeUp = viewModel::sendWinTab,
                        )
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxWidth()
                            .onTwoFingerPinchOut(
                                enabled = settings.pinchToMinimalEnabled,
                                onPinchOut = onPinchOutToMinimal,
                            ),
                    ) {
                        PadHeader(presets = presets)
                        PadArea(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            layout = layout,
                            settings = settings,
                            onButton = viewModel::sendButton,
                            onKeyboardKey = viewModel::sendKeyboardKey,
                        )
                    }
                    PresetSwipeGap(
                        modifier = Modifier
                            .weight(0.12f)
                            .fillMaxWidth(),
                        enabled = presetSwipeEnabled,
                        vertical = false,
                        onSwipeForward = viewModel::switchPresetNext,
                        onSwipeBack = viewModel::switchPresetPrevious,
                    )
                    key(inputSessionKey) {
                        TouchpadArea(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxWidth(),
                            settings = settings,
                            inputSessionKey = inputSessionKey,
                            onMove = viewModel::sendMouseMove,
                            onSingleClick = viewModel::sendMouseLeftClick,
                            onDoubleClick = viewModel::sendMouseDoubleClick,
                            onRightClick = viewModel::sendMouseRightClick,
                            onScroll = { dy, dx -> viewModel.sendMouseScroll(dy, dx) },
                            onThreeFingerSwipeLeft = viewModel::sendAltShiftTab,
                            onThreeFingerSwipeRight = viewModel::sendAltTab,
                            onThreeFingerSwipeUp = viewModel::sendWinTab,
                        )
                    }
                }
            }
        }

        if (textInputVisible && landscapeSplit) {
            TextInputOverlay(
                onSend = { text ->
                    viewModel.sendText(text)
                    viewModel.hideTextInput()
                },
                onDismiss = viewModel::hideTextInput,
            )
        }
    }
}

@Composable
private fun PresetSwipeGap(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    vertical: Boolean,
    onSwipeForward: () -> Unit,
    onSwipeBack: () -> Unit,
) {
    val swipeModifier = if (vertical) {
        Modifier.presetVerticalSwipeHandler(
            enabled = enabled,
            onSwipeUp = onSwipeForward,
            onSwipeDown = onSwipeBack,
        )
    } else {
        Modifier.presetHorizontalSwipeHandler(
            enabled = enabled,
            onSwipeLeft = onSwipeForward,
            onSwipeRight = onSwipeBack,
        )
    }

    Box(
        modifier = modifier.then(swipeModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Text(
                text = if (vertical) "↑↓ 切换方案" else "←→ 切换方案",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun TextInputPanel(
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextInputForm(
            text = text,
            onTextChange = { text = it },
            onSend = {
                if (text.isNotBlank()) onSend(text)
            },
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TextInputOverlay(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            TextInputForm(
                text = text,
                onTextChange = { text = it },
                onSend = {
                    if (text.isNotBlank()) onSend(text)
                },
                onDismiss = onDismiss,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TextInputForm(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface.copy(alpha = 0.6f),
        focusedContainerColor = scheme.surfaceVariant,
        unfocusedContainerColor = scheme.surfaceVariant,
        disabledContainerColor = scheme.surfaceVariant,
        cursorColor = scheme.primary,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "文本输入 · 写入 PC 剪贴板并粘贴（支持中文）",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    "输入文字…",
                    color = scheme.onSurfaceVariant,
                )
            },
            minLines = 3,
            colors = fieldColors,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("取消", color = scheme.onSurface)
            }
            Button(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("发送到 PC")
            }
        }
    }
}

private fun Modifier.presetHorizontalSwipeHandler(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
            onDragEnd = {
                when {
                    totalDrag < -80f -> onSwipeLeft()
                    totalDrag > 80f -> onSwipeRight()
                }
            },
        )
    }
}

private fun Modifier.presetVerticalSwipeHandler(
    enabled: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        var totalDrag = 0f
        detectVerticalDragGestures(
            onDragStart = { totalDrag = 0f },
            onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
            onDragEnd = {
                when {
                    totalDrag < -80f -> onSwipeUp()
                    totalDrag > 80f -> onSwipeDown()
                }
            },
        )
    }
}

@Composable
private fun PadArea(
    modifier: Modifier = Modifier,
    layout: ButtonLayout,
    settings: AppSettings,
    onButton: (RemoteButton) -> Unit,
    onKeyboardKey: (KeyboardKey) -> Unit,
) {
    Box(modifier = modifier) {
        when (layout.layoutMode) {
            LayoutMode.FULL_KEYBOARD -> FullKeyboardPad(settings, onKeyboardKey)
            LayoutMode.SINGLE -> SingleDualPad(layout.buttons.take(1), 1, settings, onButton)
            LayoutMode.DUAL -> SingleDualPad(layout.buttons.take(2), 2, settings, onButton)
            else -> {
                if (layout.layoutMode.isGrid) {
                    SpanGridPad(
                        buttons = layout.buttons,
                        columns = layout.effectiveColumns(),
                        rows = layout.layoutMode.rows,
                        settings = settings,
                        onButton = onButton,
                    )
                } else {
                    GridPad(layout.buttons, layout.effectiveColumns(), settings, onButton)
                }
            }
        }
    }
}

@Composable
private fun PadHeader(
    presets: com.yaokongqi.remote.model.LayoutPresetCollection,
    visible: Boolean = true,
) {
    if (!visible) return
    val activeName = presets.activePreset()?.name ?: "默认"
    if (presets.presets.size > 1) {
        Text(
            text = activeName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpanGridPad(
    buttons: List<RemoteButton>,
    columns: Int,
    rows: Int,
    settings: AppSettings,
    onButton: (RemoteButton) -> Unit,
) {
    val gap = 8.dp
    val padding = 8.dp
    val placements = remember(buttons, columns, rows) {
        GridLayoutHelper.computePlacements(buttons, columns, rows)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        val cellWidth = (maxWidth - gap * (columns - 1).coerceAtLeast(0)) / columns.coerceAtLeast(1)
        val cellHeight = (maxHeight - gap * (rows - 1).coerceAtLeast(0)) / rows.coerceAtLeast(1)

        placements.forEach { placement ->
            val button = placement.button
            key(button.id) {
                val x = (cellWidth + gap) * placement.col
                val y = (cellHeight + gap) * placement.row
                val w = cellWidth * button.colSpan + gap * (button.colSpan - 1)
                val h = cellHeight * button.rowSpan + gap * (button.rowSpan - 1)

                ActionPadButton(
                    button = button,
                    settings = settings,
                    onAction = onButton,
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(w)
                        .height(h),
                    height = null,
                )
            }
        }
    }
}

@Composable
private fun GridPad(
    buttons: List<RemoteButton>,
    columns: Int,
    settings: AppSettings,
    onButton: (RemoteButton) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(buttons, key = { it.id }, contentType = { "pad_button" }) { button ->
            key(button.id) {
                ActionPadButton(button = button, settings = settings, onAction = onButton, height = 56.dp)
            }
        }
    }
}

@Composable
private fun SingleDualPad(
    buttons: List<RemoteButton>,
    columns: Int,
    settings: AppSettings,
    onButton: (RemoteButton) -> Unit,
) {
    if (buttons.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请在「设置 → 编辑按钮」中添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(buttons, key = { it.id }) { button ->
            key(button.id) {
                ActionPadButton(button = button, settings = settings, onAction = onButton, height = 120.dp)
            }
        }
    }
}

@Composable
private fun FullKeyboardPad(settings: AppSettings, onKey: (KeyboardKey) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FullKeyboardLayout.rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                row.forEachIndexed { keyIndex, key ->
                    key("$rowIndex-$keyIndex-${key.label}") {
                        PadButton(
                            label = key.label,
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(key.weight).height(36.dp),
                            height = 36.dp,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPadButton(
    button: RemoteButton,
    settings: AppSettings,
    onAction: (RemoteButton) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: androidx.compose.ui.unit.Dp? = 56.dp,
) {
    val supportsLongPress = button.action == ButtonAction.VOLUME_UP ||
        button.action == ButtonAction.VOLUME_DOWN
    val sizedModifier = if (height != null) modifier.height(height) else modifier

    PadButton(
        label = button.label,
        onClick = { onAction(button) },
        onLongPressRepeat = if (supportsLongPress) {
            { onAction(button) }
        } else {
            null
        },
        hapticEnabled = settings.hapticFeedback,
        modifier = sizedModifier,
        height = height ?: 56.dp,
    )
}

@Composable
private fun PadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: androidx.compose.ui.unit.Dp,
    compact: Boolean = false,
    hapticEnabled: Boolean = true,
    onLongPressRepeat: (() -> Unit)? = null,
) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current

    LaunchedEffect(pressed, onLongPressRepeat) {
        if (!pressed || onLongPressRepeat == null) return@LaunchedEffect
        delay(450)
        while (true) {
            onLongPressRepeat()
            delay(120)
        }
    }

    Surface(
        onClick = {
            if (hapticEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onClick()
        },
        modifier = modifier.height(height),
        shape = RoundedCornerShape(if (compact) 6.dp else 8.dp),
        color = if (pressed) scheme.primaryContainer else scheme.surfaceVariant,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.65f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TouchpadArea(
    modifier: Modifier = Modifier,
    inputSessionKey: Int = 0,
    settings: AppSettings,
    scrollOnly: Boolean = false,
    onMove: (Float, Float) -> Unit,
    onSingleClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (deltaY: Int, deltaX: Int) -> Unit,
    onLongPress: (() -> Unit)? = null,
    onThreeFingerSwipeLeft: () -> Unit = {},
    onThreeFingerSwipeRight: () -> Unit = {},
    onThreeFingerSwipeUp: () -> Unit = {},
) {
    val scrollMode = settings.touchScrollMode
    val scrollBase = settings.touchSensitivity.scrollMultiplier
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pendingSingleTap by remember { mutableStateOf<Job?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    fun handleTap() {
        val now = System.currentTimeMillis()
        if (pendingSingleTap?.isActive == true) {
            pendingSingleTap?.cancel()
            pendingSingleTap = null
            lastTapAt = 0L
            onDoubleClick()
            return
        }
        if (now - lastTapAt in 1..350) {
            lastTapAt = 0L
            onDoubleClick()
            return
        }
        lastTapAt = now
        pendingSingleTap?.cancel()
        pendingSingleTap = scope.launch {
            delay(300)
            onSingleClick()
            pendingSingleTap = null
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (!scrollOnly && settings.showTouchpadHint) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "触摸板",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
                Text(
                    buildTouchpadHint(settings, scrollMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .laptopTrackpadGestures(
                    sessionKey = inputSessionKey,
                    scrollOnly = scrollOnly,
                    scrollMode = scrollMode && !scrollOnly,
                    scrollMultiplier = scrollBase,
                    callbacks = TrackpadCallbacks(
                        onMove = { dx, dy ->
                            onMove(dx, dy)
                        },
                        onScroll = { dy, dx ->
                            onScroll(dy, dx)
                        },
                        onSingleClick = { if (!scrollOnly) handleTap() },
                        onDoubleClick = {},
                        onRightClick = { if (!scrollOnly) onRightClick() },
                        onLongPress = onLongPress,
                        onThreeFingerSwipeLeft = onThreeFingerSwipeLeft,
                        onThreeFingerSwipeRight = onThreeFingerSwipeRight,
                        onThreeFingerSwipeUp = onThreeFingerSwipeUp,
                        onMultiTouchActive = { active ->
                            (view.parent as? android.view.ViewParent)
                                ?.requestDisallowInterceptTouchEvent(active)
                        },
                    ),
                ),
        )
    }
}

private fun buildTouchpadHint(settings: AppSettings, scrollMode: Boolean): String {
    val base = buildString {
        append("单指移鼠标 · 双指滚轮 · 双指点按右键\n")
        append("四指左右切窗 · 四指上/下滑任务视图 · 三指轻点也可\n")
        append("（三指滑动手势若无效，多为系统截图占用）")
        if (scrollMode) append("\n单指竖滑滚轮")
        append(" · 灵敏度${settings.touchSensitivity.label}")
    }
    return if (settings.pinchToMinimalEnabled) "$base · 双指外扩进极简滚轮" else base
}

@Composable
fun MinimalScrollScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    onExit: () -> Unit,
) {
    val inputSessionKey by viewModel.inputSessionKey.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(inputSessionKey) {
            TouchpadArea(
                modifier = Modifier.fillMaxSize(),
                settings = settings,
                inputSessionKey = inputSessionKey,
                scrollOnly = true,
                onMove = { _, _ -> },
                onSingleClick = {},
                onDoubleClick = {},
                onRightClick = {},
                onScroll = { dy, _ -> viewModel.sendMouseScroll(dy) },
                onLongPress = onExit,
            )
        }
        Text(
            text = "极简滚轮 · 长按或点按钮退出",
            color = Color.White.copy(alpha = 0.28f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )
        TextButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text("退出极简模式", color = Color.White.copy(alpha = 0.72f))
        }
    }
}
