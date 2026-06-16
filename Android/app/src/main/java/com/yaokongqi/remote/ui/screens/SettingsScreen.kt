package com.yaokongqi.remote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.LayoutMode
import com.yaokongqi.remote.model.TouchSensitivity
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.theme.Primary
import com.yaokongqi.remote.ui.util.findActivity
import com.yaokongqi.remote.ui.util.setGamepadLandscapeLock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var draft by remember { mutableStateOf<AppSettings?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        draft = viewModel.loadSettingsForEdit()
    }

    val current = draft ?: run {
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val layoutEditable = viewModel.isActiveLayoutEditable()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.discardSettingsEdit()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            SettingsBottomBar(
                onSave = {
                    viewModel.saveSettings(current)
                    onSaved()
                },
                onCancel = {
                    viewModel.discardSettingsEdit()
                    onBack()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GamepadSettingsSection(
                current = current,
                onDraftChange = { draft = it },
                viewModel = viewModel,
                context = context,
                onSaved = onSaved,
            )

            SettingsSection(title = "外观") {
                RowSwitch(
                    title = "深色模式",
                    subtitle = "遥控界面使用深色背景，夜间更护眼",
                    checked = current.darkMode,
                    onCheckedChange = { draft = current.copy(darkMode = it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                RowSwitch(
                    title = "按键震动反馈",
                    subtitle = "按下遥控按钮时触发震动",
                    checked = current.hapticFeedback,
                    onCheckedChange = { draft = current.copy(hapticFeedback = it) },
                )
            }

            SettingsSection(title = "布局方案") {
                LayoutPresetSection(
                    viewModel = viewModel,
                    onPresetSwitched = {
                        draft = draft?.let { viewModel.syncDraftLayoutFromActivePreset(it) }
                    },
                )
            }

            SettingsSection(title = "按键布局") {
                if (!layoutEditable) {
                    Text(
                        "内置场景方案的网格与按键已锁定，仅可重命名；如需自定义请保存为新方案。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LayoutMode.selectable.forEach { mode ->
                        FilterChip(
                            selected = current.layoutMode == mode,
                            onClick = {
                                if (layoutEditable) {
                                    draft = current.copy(layoutMode = mode)
                                    viewModel.applyLayoutModeForEdit(mode)
                                }
                            },
                            enabled = layoutEditable,
                            label = { Text(mode.label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                RowSwitch(
                    title = "横屏分栏布局",
                    subtitle = "横屏时左侧按键、中间留白、右侧触摸板",
                    checked = current.landscapeSplitLayout,
                    onCheckedChange = { draft = current.copy(landscapeSplitLayout = it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                ButtonEditSection(
                    viewModel = viewModel,
                    layoutMode = current.layoutMode,
                    layoutEditable = layoutEditable,
                )
            }

            SettingsSection(title = "触摸板") {
                RowSwitch(
                    title = "显示触摸板提示",
                    subtitle = "关闭后触摸板区域不再显示操作说明文字",
                    checked = current.showTouchpadHint,
                    onCheckedChange = { draft = current.copy(showTouchpadHint = it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                RowSwitch(
                    title = "竖滑滚轮",
                    subtitle = "关闭时触摸板仅移动鼠标，不发送滚轮",
                    checked = current.touchScrollMode,
                    onCheckedChange = { draft = current.copy(touchScrollMode = it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("触摸灵敏度", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchSensitivity.selectable.forEach { level ->
                        FilterChip(
                            selected = current.touchSensitivity == level,
                            onClick = { draft = current.copy(touchSensitivity = level) },
                            label = { Text(level.label) },
                        )
                    }
                }
            }

            SettingsSection(title = "极简滚轮") {
                RowSwitch(
                    title = "双指外扩手势",
                    subtitle = "6:4 布局下双指外扩进入极简滚轮",
                    checked = current.pinchToMinimalEnabled,
                    onCheckedChange = { draft = current.copy(pinchToMinimalEnabled = it) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.saveSettings(current)
                        viewModel.enterMinimalScrollMode()
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存并进入极简滚轮")
                }
            }

            SettingsSection(title = "设备") {
                Text("已保存：${viewModel.savedPcName ?: viewModel.savedHost ?: "无"}")
                Text(
                    "多设备管理请在连接页操作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                RowSwitch(
                    title = "连接状态通知",
                    subtitle = "在通知栏显示已连接设备；开启有助于锁屏时保持连接",
                    checked = current.showConnectionNotification,
                    onCheckedChange = { draft = current.copy(showConnectionNotification = it) },
                )
            }

            SettingsSection(title = "关于与法律信息") {
                LegalInfoSection()
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamepadSettingsSection(
    current: AppSettings,
    onDraftChange: (AppSettings) -> Unit,
    viewModel: MainViewModel,
    context: Context,
    onSaved: () -> Unit,
) {
    SettingsSection(title = "射击游戏（虚拟手柄）") {
        RowSwitch(
            title = "射击游戏模式",
            subtitle = "横屏虚拟 Xbox 手柄；PC 需安装 ViGEmBus 驱动，游戏内请拔掉实体手柄",
            checked = current.shooterGamepadMode,
            onCheckedChange = { onDraftChange(current.copy(shooterGamepadMode = it)) },
        )
        if (current.shooterGamepadMode) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                "发送频率 ${current.gamepadPollHz} Hz（兜底同步）· 瞄准/按键已即时发送" +
                    if (current.gamepadUseUdp) " · UDP 已开" else " · 仅 WebSocket",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Wi-Fi 稳定推荐 250 或 500 Hz；卡顿时可降到 120",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(120, 180, 250, 500).forEach { hz ->
                    FilterChip(
                        selected = current.gamepadPollHz == hz,
                        onClick = { onDraftChange(current.copy(gamepadPollHz = hz)) },
                        label = {
                            Text(
                                when (hz) {
                                    250 -> "${hz}Hz（默认）"
                                    500 -> "${hz}Hz（低延迟）"
                                    else -> "${hz}Hz"
                                },
                            )
                        },
                    )
                }
            }
            RowSwitch(
                title = "UDP 低延迟通道",
                subtitle = "配对后走 udp://PC:10826；需 PC 0.2.6+",
                checked = current.gamepadUseUdp,
                onCheckedChange = { onDraftChange(current.copy(gamepadUseUdp = it)) },
            )
            Text(
                "移动",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            GamepadSliderSetting(
                label = "移动灵敏度",
                value = current.moveSensitivity,
                range = AppSettings.MOVE_SENS_MIN..AppSettings.MOVE_SENS_MAX,
                hint = "左半屏移动轮盘",
                onChange = { onDraftChange(current.copy(moveSensitivity = it)) },
            )
            GamepadSliderSetting(
                label = "移动死区",
                value = current.moveDeadzone,
                range = AppSettings.MOVE_DEADZONE_MIN..AppSettings.MOVE_DEADZONE_MAX,
                hint = "轮盘中心无响应区，越大越不易误触",
                onChange = { onDraftChange(current.copy(moveDeadzone = it)) },
            )
            Text(
                "腰射瞄准",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            GamepadSliderSetting(
                label = "水平",
                value = current.aimSensitivityX,
                range = AppSettings.AIM_SENS_X_MIN..AppSettings.AIM_SENS_X_MAX,
                hint = "未开镜、未开火；环顾与转身",
                onChange = {
                    onDraftChange(
                        current.copy(
                            aimSensitivityX = it,
                            aimSensitivity = it,
                        ),
                    )
                },
            )
            GamepadSliderSetting(
                label = "垂直",
                value = current.aimSensitivityY,
                range = AppSettings.AIM_SENS_Y_MIN..AppSettings.AIM_SENS_Y_MAX,
                hint = "未开镜、未开火；上下看",
                onChange = { onDraftChange(current.copy(aimSensitivityY = it)) },
            )
            Text(
                "开镜瞄准",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            GamepadSliderSetting(
                label = "水平",
                value = current.adsAimSensitivityX,
                range = AppSettings.AIM_SENS_X_MIN..AppSettings.AIM_SENS_X_MAX,
                hint = "开镜跟枪；通常低于腰射",
                onChange = { onDraftChange(current.copy(adsAimSensitivityX = it)) },
            )
            GamepadSliderSetting(
                label = "垂直",
                value = current.adsAimSensitivityY,
                range = AppSettings.AIM_SENS_Y_MIN..AppSettings.AIM_SENS_Y_MAX,
                hint = "开镜上下微调",
                onChange = { onDraftChange(current.copy(adsAimSensitivityY = it)) },
            )
            Text(
                "开火瞄准",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            GamepadSliderSetting(
                label = "腰射开火 · 水平",
                value = current.fireAimSensitivityX,
                range = AppSettings.AIM_SENS_X_MIN..AppSettings.AIM_SENS_X_MAX,
                onChange = { onDraftChange(current.copy(fireAimSensitivityX = it)) },
            )
            GamepadSliderSetting(
                label = "腰射开火 · 垂直",
                value = current.fireAimSensitivityY,
                range = AppSettings.AIM_SENS_Y_MIN..AppSettings.AIM_SENS_Y_MAX,
                hint = "压枪时可略低于腰射",
                onChange = { onDraftChange(current.copy(fireAimSensitivityY = it)) },
            )
            GamepadSliderSetting(
                label = "开镜开火 · 水平",
                value = current.fireAdsAimSensitivityX,
                range = AppSettings.AIM_SENS_X_MIN..AppSettings.AIM_SENS_X_MAX,
                onChange = { onDraftChange(current.copy(fireAdsAimSensitivityX = it)) },
            )
            GamepadSliderSetting(
                label = "开镜开火 · 垂直",
                value = current.fireAdsAimSensitivityY,
                range = AppSettings.AIM_SENS_Y_MIN..AppSettings.AIM_SENS_Y_MAX,
                hint = "开镜压枪；偏低更稳",
                onChange = { onDraftChange(current.copy(fireAdsAimSensitivityY = it)) },
            )
            Text(
                "高级",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            GamepadSliderSetting(
                label = "腰射滑动加速",
                value = current.aimSwipeAcceleration,
                range = AppSettings.AIM_SWIPE_ACCEL_MIN..AppSettings.AIM_SWIPE_ACCEL_MAX,
                hint = "仅腰射；0 匀速，越大甩枪越快；开镜/开火线性",
                onChange = { onDraftChange(current.copy(aimSwipeAcceleration = it)) },
            )
                    GamepadSliderSetting(
                        label = "瞄准平滑",
                        value = current.aimSmoothing,
                        range = AppSettings.AIM_SMOOTHING_MIN..AppSettings.AIM_SMOOTHING_MAX,
                        hint = "越大越稳、越小越跟手；甩枪时自动减弱",
                        onChange = { onDraftChange(current.copy(aimSmoothing = it)) },
                    )
                    RowSwitch(
                        title = "瞄准静止衰减",
                        subtitle = "手指停住约 45ms 后视角逐渐停止，避免划完仍持续转动",
                        checked = current.aimIdleDecay,
                        onCheckedChange = { onDraftChange(current.copy(aimIdleDecay = it)) },
                    )
                    GamepadSliderSetting(
                label = "控件透明度",
                value = current.gamepadControlAlpha,
                range = AppSettings.GAMEPAD_ALPHA_MIN..AppSettings.GAMEPAD_ALPHA_MAX,
                onChange = { onDraftChange(current.copy(gamepadControlAlpha = it)) },
            )
            Text(
                "游戏中仅按键与移动轮盘可见；瞄准区游戏中透明，编辑布局时显示参考框",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            RowSwitch(
                title = "非固定移动轮盘",
                subtitle = "关闭时左下固定小轮盘（推荐）；开启则在触点出现轮盘",
                checked = current.gamepadFloatingStick,
                onCheckedChange = { onDraftChange(current.copy(gamepadFloatingStick = it)) },
            )
            Text(
                "左半屏移动、右半屏瞄准；编辑布局中可为每个键单独调大小与透明度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = {
                    viewModel.saveSettings(current)
                    context.findActivity()?.setGamepadLandscapeLock(true)
                    viewModel.requestGamepadLayoutEdit()
                    onSaved()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("编辑按键布局")
            }
            OutlinedButton(
                onClick = {
                    onDraftChange(viewModel.resetGamepadTuning(current))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("恢复灵敏度默认值")
            }
            OutlinedButton(
                onClick = {
                    onDraftChange(viewModel.resetGamepadLayout(current))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("恢复 Apex 默认布局")
            }
            Button(
                onClick = {
                    val updated = current.copy(shooterGamepadMode = false)
                    viewModel.saveSettings(updated)
                    onDraftChange(updated)
                    onSaved()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("退出游戏模式")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsBottomBar(onSave: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) { Text("保存设置") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
    }
}

@Composable
private fun GamepadSliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    hint: String? = null,
    onChange: (Float) -> Unit,
) {
    Text(
        "$label：${"%.2f".format(value)}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (hint != null) {
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
    )
}

@Composable
private fun RowSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
