package com.yaokongqi.remote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yaokongqi.remote.model.SavedDevice
import com.yaokongqi.remote.ui.theme.Primary

@Composable
fun PairScreen(
    devices: List<SavedDevice>,
    defaultHost: String?,
    onPair: (host: String, pin: String) -> Unit,
    onReconnect: (host: String) -> Unit,
    onRemoveDevice: (host: String) -> Unit,
    onClearAll: () -> Unit,
    errorMessage: String?,
) {
    var host by rememberSaveable { mutableStateOf(defaultHost ?: "") }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = if (devices.isNotEmpty()) "重新连接" else "首次配对",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "选择已保存设备，或输入新 IP 与 PIN 配对",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isNotEmpty()) {
            Text("已保存设备", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            devices.forEach { device ->
                SavedDeviceRow(
                    device = device,
                    selected = host == device.host,
                    onSelect = {
                        host = device.host
                        onReconnect(device.host)
                    },
                    onDelete = { onRemoveDevice(device.host) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        Text("添加新设备 / 更换电脑", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("电脑 IP（不含端口）") },
            placeholder = { Text("例如 192.168.1.100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("配对 PIN（仅首次需要）") },
            singleLine = true,
            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                IconButton(onClick = { pinVisible = !pinVisible }) {
                    Icon(
                        if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (pinVisible) "隐藏 PIN" else "显示 PIN",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onPair(host, pin) },
            enabled = host.isNotBlank() && pin.length == 6,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("配对并连接")
        }

        if (devices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
                Text("清除全部已保存设备")
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SavedDeviceRow(
    device: SavedDevice,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.pcName, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    device.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除设备")
            }
        }
    }
}
