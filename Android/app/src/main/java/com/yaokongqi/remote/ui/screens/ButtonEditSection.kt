package com.yaokongqi.remote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yaokongqi.remote.model.ButtonAction
import com.yaokongqi.remote.model.GridLayoutHelper
import com.yaokongqi.remote.model.GridSpanOption
import com.yaokongqi.remote.model.KeyPresets
import com.yaokongqi.remote.model.LayoutMode
import com.yaokongqi.remote.model.RemoteButton
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.theme.Primary
import java.util.UUID

@Composable
fun ButtonEditSection(
    viewModel: MainViewModel,
    layoutMode: LayoutMode,
    layoutEditable: Boolean = true,
) {
    val layout by viewModel.buttonLayout.collectAsState()
    val effectiveLayoutMode = layout.layoutMode
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RemoteButton?>(null) }
    var pendingGridRow by remember { mutableIntStateOf(-1) }
    var pendingGridCol by remember { mutableIntStateOf(-1) }
    var addStoreError by remember { mutableStateOf<String?>(null) }
    val canEdit = effectiveLayoutMode != LayoutMode.FULL_KEYBOARD
    val maxButtons = effectiveLayoutMode.maxButtons
    val columns = effectiveLayoutMode.columns
    val rows = effectiveLayoutMode.rows
    val activeButtons = remember(layout.buttons, maxButtons) {
        layout.buttons.take(maxButtons)
    }

    if (!canEdit) {
        Text(
            "全键模式使用内置完整键盘，无需编辑自定义按钮。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (!layoutEditable) {
        Text(
            "当前为内置场景方案，布局与按键不可修改；可在上方重命名，或「保存为自定义方案」后再编辑。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    val occupiedCells = if (effectiveLayoutMode.isGrid) {
        GridLayoutHelper.occupiedCells(activeButtons, columns, rows)
    } else {
        activeButtons.size
    }
    val maxCells = if (effectiveLayoutMode.isGrid) GridLayoutHelper.maxCells(columns, rows) else maxButtons

    Text(
        if (effectiveLayoutMode.isGrid) {
            "最多 $maxButtons 个按键，占格 $occupiedCells / $maxCells"
        } else {
            "最多 $maxButtons 个，当前 ${activeButtons.size} 个"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (effectiveLayoutMode.isGrid) {
        LayoutGridPreview(
            buttons = activeButtons,
            columns = columns,
            rows = rows,
            modifier = Modifier.fillMaxWidth(),
            editable = layoutEditable,
            selectedButtonId = if (layoutEditable) editing?.id else null,
            onCellClick = if (layoutEditable) {
                { row, col, occupied ->
                    if (occupied != null) {
                        editing = occupied
                    } else if (activeButtons.size < maxButtons) {
                        pendingGridRow = row
                        pendingGridCol = col
                        addStoreError = null
                        showAddDialog = true
                    }
                }
            } else {
                null
            },
            onButtonClick = if (layoutEditable) {
                { editing = it }
            } else {
                null
            },
            onButtonMove = if (layoutEditable) {
                { buttonId, row, col ->
                    val button = activeButtons.find { it.id == buttonId } ?: return@LayoutGridPreview
                    viewModel.updateButton(
                        button.copy(
                            gridRow = row,
                            gridCol = col,
                        ),
                        effectiveLayoutMode,
                    )
                }
            } else {
                null
            },
            minHeight = 220.dp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (layoutEditable) {
                "点击空白格添加按键；长按按键拖动到目标位置（跨格大键可整体移动）；短按编辑。"
            } else {
                "以下为当前方案预览（只读）。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (layoutEditable) {
        layout.buttons.take(maxButtons).forEach { button ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val spanHint = if (effectiveLayoutMode.isGrid && (button.colSpan > 1 || button.rowSpan > 1)) {
                    " (${button.colSpan}×${button.rowSpan})"
                } else {
                    ""
                }
                Text(
                    text = button.label + spanHint,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { editing = button }) { Text("编辑") }
                IconButton(onClick = { viewModel.removeButton(button.id, effectiveLayoutMode) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    pendingGridRow = -1
                    pendingGridCol = -1
                    showAddDialog = true
                },
                enabled = activeButtons.size < maxButtons,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("添加")
            }
            TextButton(onClick = { viewModel.resetLayout() }) {
                Text("恢复默认")
            }
        }
    }

    if (layoutEditable && showAddDialog) {
        ButtonEditDialog(
            title = "添加按钮",
            initial = null,
            layoutMode = effectiveLayoutMode,
            gridRow = pendingGridRow,
            gridCol = pendingGridCol,
            existingButtons = activeButtons,
            onDismiss = {
                showAddDialog = false
                pendingGridRow = -1
                pendingGridCol = -1
                addStoreError = null
            },
            onConfirm = {
                if (viewModel.addButton(it, effectiveLayoutMode)) {
                    showAddDialog = false
                    pendingGridRow = -1
                    pendingGridCol = -1
                    addStoreError = null
                } else {
                    addStoreError = "添加失败：网格已满或当前位置无法放置，请换位置或缩小占格"
                }
            },
            externalError = addStoreError,
        )
    }
    if (layoutEditable) {
        editing?.let { button ->
            ButtonEditDialog(
                title = "编辑按钮",
                initial = button,
                layoutMode = effectiveLayoutMode,
                gridRow = button.gridRow,
                gridCol = button.gridCol,
                existingButtons = activeButtons.filter { it.id != button.id },
                onDismiss = { editing = null },
                onConfirm = {
                    viewModel.updateButton(it, effectiveLayoutMode)
                    editing = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ButtonEditDialog(
    title: String,
    initial: RemoteButton?,
    layoutMode: LayoutMode,
    gridRow: Int = -1,
    gridCol: Int = -1,
    existingButtons: List<RemoteButton> = emptyList(),
    externalError: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (RemoteButton) -> Unit,
) {
    val columns = layoutMode.columns
    val rows = layoutMode.rows
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var selectedPreset by remember {
        mutableStateOf(
            KeyPresets.all.find {
                it.action == (initial?.action ?: ButtonAction.KEY) &&
                    it.vk == (initial?.vk ?: 0) &&
                    it.mods == (initial?.mods ?: 0)
            } ?: KeyPresets.all.first(),
        )
    }
    val spanOptions = remember(layoutMode) {
        if (layoutMode.isGrid) GridLayoutHelper.spanOptions(columns, rows) else listOf(GridSpanOption("1×1", 1, 1))
    }
    var selectedSpan by remember {
        mutableStateOf(
            spanOptions.find {
                it.colSpan == (initial?.colSpan ?: 1) && it.rowSpan == (initial?.rowSpan ?: 1)
            } ?: spanOptions.first(),
        )
    }
    var placementError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("按钮名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedPreset.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("对应功能") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        KeyPresets.all.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    selectedPreset = preset
                                    if (label.isBlank() || KeyPresets.all.any { it.label == label }) {
                                        label = preset.label.substringBefore(' ')
                                    }
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                if (layoutMode.isGrid) {
                    Text("占格大小", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        spanOptions.forEach { option ->
                            FilterChip(
                                selected = selectedSpan == option,
                                onClick = { selectedSpan = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    if (gridRow >= 0 && gridCol >= 0) {
                        Text(
                            "位置：第 ${gridRow + 1} 行，第 ${gridCol + 1} 列",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                placementError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                externalError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = label.ifBlank { selectedPreset.label.substringBefore(' ') }
                    val candidate = GridLayoutHelper.prepareForGrid(
                        RemoteButton(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            label = name,
                            vk = selectedPreset.vk,
                            mods = selectedPreset.mods,
                            action = selectedPreset.action,
                            colSpan = selectedSpan.colSpan,
                            rowSpan = selectedSpan.rowSpan,
                            gridCol = if (gridCol >= 0) gridCol else initial?.gridCol ?: -1,
                            gridRow = if (gridRow >= 0) gridRow else initial?.gridRow ?: -1,
                        ),
                        columns,
                        rows,
                    )
                    if (layoutMode.isGrid &&
                        !GridLayoutHelper.canAddButton(existingButtons, candidate, columns, rows)
                    ) {
                        placementError = "当前占格无法放入网格，请缩小尺寸或更换位置"
                        return@TextButton
                    }
                    placementError = null
                    onConfirm(candidate)
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
