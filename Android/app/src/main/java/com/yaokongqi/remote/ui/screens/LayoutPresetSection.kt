package com.yaokongqi.remote.ui.screens



import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.FlowRow

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material.icons.filled.Edit

import androidx.compose.material3.AlertDialog

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

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import com.yaokongqi.remote.model.LayoutPreset

import com.yaokongqi.remote.model.ScenarioLayoutPresets

import com.yaokongqi.remote.ui.MainViewModel



@OptIn(ExperimentalLayoutApi::class)

@Composable

fun LayoutPresetSection(

    viewModel: MainViewModel,

    onPresetSwitched: () -> Unit = {},

) {

    val presets by viewModel.layoutPresets.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }

    var newPresetName by remember { mutableStateOf("") }

    var renamingPreset by remember { mutableStateOf<LayoutPreset?>(null) }

    var renameValue by remember { mutableStateOf("") }

    val active = presets.activePreset()



    Text(

        "在按键与触摸板之间的空白区域滑动可快速切换方案（竖屏左右滑、横屏上下滑）。内置方案仅可重命名，自定义方案可编辑按键。",

        style = MaterialTheme.typography.bodySmall,

        color = MaterialTheme.colorScheme.onSurfaceVariant,

    )

    Spacer(modifier = Modifier.height(8.dp))



    FlowRow(

        horizontalArrangement = Arrangement.spacedBy(8.dp),

        verticalArrangement = Arrangement.spacedBy(8.dp),

    ) {

        presets.presets.forEachIndexed { index, preset ->

            FilterChip(

                selected = index == presets.activeIndex,

                onClick = {

                    viewModel.switchPresetTo(index)

                    onPresetSwitched()

                },

                label = { Text(preset.name) },

            )

        }

    }



    active?.let { preset ->

        ScenarioLayoutPresets.descriptionFor(preset)?.let { desc ->

            Spacer(modifier = Modifier.height(6.dp))

            Text(

                desc,

                style = MaterialTheme.typography.bodySmall,

                color = MaterialTheme.colorScheme.primary,

            )

        }

    }



    Spacer(modifier = Modifier.height(8.dp))



    presets.presets.forEach { preset ->

        val builtin = ScenarioLayoutPresets.isBuiltin(preset.id)

        Row(

            modifier = Modifier.fillMaxWidth(),

            verticalAlignment = Alignment.CenterVertically,

        ) {

            Column(modifier = Modifier.weight(1f)) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(preset.name)

                    if (builtin) {

                        Text(

                            " · 内置",

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                        )

                    }

                }

                ScenarioLayoutPresets.descriptionFor(preset)?.let {

                    Text(

                        it,

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )

                } ?: Text(

                    "${preset.layout.layoutMode.label} · ${preset.layout.buttons.size} 键",

                    style = MaterialTheme.typography.bodySmall,

                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                )

            }

            IconButton(

                onClick = {

                    renamingPreset = preset

                    renameValue = preset.name

                },

            ) {

                Icon(Icons.Default.Edit, contentDescription = "重命名")

            }

            if (!builtin && presets.presets.size > 1) {

                IconButton(onClick = { viewModel.deletePreset(preset.id) }) {

                    Icon(Icons.Default.Delete, contentDescription = "删除")

                }

            }

        }

    }



    Spacer(modifier = Modifier.height(8.dp))

    TextButton(onClick = { showSaveDialog = true }) {

        Text("保存当前布局为自定义方案")

    }



    if (showSaveDialog) {

        AlertDialog(

            onDismissRequest = { showSaveDialog = false },

            title = { Text("保存自定义方案") },

            text = {

                OutlinedTextField(

                    value = newPresetName,

                    onValueChange = { newPresetName = it },

                    label = { Text("方案名称") },

                    singleLine = true,

                    modifier = Modifier.fillMaxWidth(),

                )

            },

            confirmButton = {

                TextButton(

                    onClick = {

                        val name = newPresetName.ifBlank { "方案 ${presets.presets.size + 1}" }

                        viewModel.saveCurrentAsPreset(name)

                        newPresetName = ""

                        showSaveDialog = false

                        onPresetSwitched()

                    },

                ) { Text("保存") }

            },

            dismissButton = {

                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }

            },

        )

    }



    renamingPreset?.let { preset ->

        AlertDialog(

            onDismissRequest = { renamingPreset = null },

            title = { Text("重命名方案") },

            text = {

                Column {

                    if (ScenarioLayoutPresets.isBuiltin(preset.id)) {

                        Text(

                            "内置方案只能修改显示名称，按键布局保持不变。",

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.padding(bottom = 8.dp),

                        )

                    }

                    OutlinedTextField(

                        value = renameValue,

                        onValueChange = { renameValue = it },

                        label = { Text("方案名称") },

                        singleLine = true,

                        modifier = Modifier.fillMaxWidth(),

                    )

                }

            },

            confirmButton = {

                TextButton(

                    onClick = {

                        val name = renameValue.trim()

                        if (name.isNotEmpty()) {

                            viewModel.renamePreset(preset.id, name)

                        }

                        renamingPreset = null

                    },

                ) { Text("确定") }

            },

            dismissButton = {

                TextButton(onClick = { renamingPreset = null }) { Text("取消") }

            },

        )

    }

}

