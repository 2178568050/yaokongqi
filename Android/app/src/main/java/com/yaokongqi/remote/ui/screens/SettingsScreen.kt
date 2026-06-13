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
import androidx.compose.ui.unit.dp
import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.LayoutMode
import com.yaokongqi.remote.model.TouchSensitivity
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var draft by remember { mutableStateOf<AppSettings?>(null) }

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
                                if (layoutEditable) draft = current.copy(layoutMode = mode)
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
            }

            SettingsSection(title = "关于与法律信息") {
                LegalInfoSection()
            }

            Spacer(modifier = Modifier.height(72.dp))
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
