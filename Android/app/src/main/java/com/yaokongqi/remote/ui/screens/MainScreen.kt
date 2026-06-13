package com.yaokongqi.remote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.yaokongqi.remote.connection.ConnectionState
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.components.ExitConfirmLayer
import com.yaokongqi.remote.ui.navigation.MainRoute
import com.yaokongqi.remote.ui.navigation.backwardTransition
import com.yaokongqi.remote.ui.navigation.forwardTransition
import com.yaokongqi.remote.ui.navigation.minimalEnterTransition
import com.yaokongqi.remote.ui.navigation.minimalExitTransition
import com.yaokongqi.remote.ui.theme.Connected
import com.yaokongqi.remote.ui.theme.ErrorColor

private enum class SubScreen { Pad, Settings }

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val info by viewModel.connectionInfo.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val devices by viewModel.deviceHistory.collectAsState()
    val presets by viewModel.layoutPresets.collectAsState()
    val minimal by viewModel.minimalScrollMode.collectAsState()
    val configuration = LocalConfiguration.current
    val landscapeCompactBar = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        settings.landscapeSplitLayout
    var subScreen by rememberSaveable { mutableStateOf(SubScreen.Pad) }
    LaunchedEffect(subScreen, info.state) {
        if (subScreen == SubScreen.Pad && info.state == ConnectionState.Connected) {
            viewModel.onAppForeground()
        }
    }


    val route = when {
        minimal && info.state == ConnectionState.Connected -> MainRoute.MinimalScroll
        subScreen == SubScreen.Settings -> MainRoute.Settings
        else -> MainRoute.Pad
    }

    val exitEnabled = route == MainRoute.Pad && subScreen == SubScreen.Pad &&
        info.state != ConnectionState.Connecting

    ExitConfirmLayer(
        enabled = exitEnabled,
        onExit = { (context as? android.app.Activity)?.finish() },
    ) {
        AnimatedContent(
            targetState = route,
            modifier = modifier.fillMaxSize(),
            transitionSpec = {
                when {
                    initialState == MainRoute.Pad && targetState == MainRoute.MinimalScroll ->
                        minimalEnterTransition()
                    initialState == MainRoute.MinimalScroll && targetState == MainRoute.Pad ->
                        minimalExitTransition()
                    targetState.ordinal > initialState.ordinal ||
                        (initialState == MainRoute.Pad && targetState != MainRoute.Pad) ->
                        forwardTransition()
                    else -> backwardTransition()
                }
            },
            label = "main_route",
        ) { current ->
            when (current) {
                MainRoute.MinimalScroll -> MinimalScrollScreen(
                    viewModel = viewModel,
                    settings = settings,
                    onExit = { viewModel.exitMinimalScrollMode() },
                )
                MainRoute.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { subScreen = SubScreen.Pad },
                    onSaved = { subScreen = SubScreen.Pad },
                )
                MainRoute.Pad -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .then(
                            if (landscapeCompactBar) Modifier else Modifier.statusBarsPadding(),
                        ),
                ) {
                    StatusBar(
                        pcName = info.pcName ?: viewModel.savedPcName,
                        state = info.state,
                        latencyMs = info.latencyMs,
                        packetLossPercent = info.packetLossPercent,
                        showControls = info.state == ConnectionState.Connected,
                        landscapeCompact = landscapeCompactBar,
                        presetName = presets.activePreset()?.name?.takeIf { presets.presets.size > 1 },
                        onTextInput = { viewModel.showTextInput() },
                        onSettings = { subScreen = SubScreen.Settings },
                        onDisconnect = { viewModel.disconnect() },
                        modifier = if (landscapeCompactBar) Modifier.statusBarsPadding() else Modifier,
                    )
                    when (info.state) {
                        ConnectionState.Connected -> ConnectedPadScreen(
                            viewModel = viewModel,
                            settings = settings,
                            onPinchOutToMinimal = { viewModel.enterMinimalScrollMode() },
                        )
                        ConnectionState.Connecting -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (viewModel.hasSavedSession) "正在连接上次设备…" else "连接中…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> PairScreen(
                            devices = devices.devices,
                            defaultHost = viewModel.savedHost,
                            onPair = viewModel::pair,
                            onReconnect = viewModel::reconnectTo,
                            onRemoveDevice = viewModel::removeDevice,
                            onClearAll = viewModel::forgetAllDevices,
                            errorMessage = if (info.state == ConnectionState.Error) info.message else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    pcName: String?,
    state: ConnectionState,
    latencyMs: Int?,
    packetLossPercent: Int,
    showControls: Boolean,
    landscapeCompact: Boolean = false,
    presetName: String? = null,
    modifier: Modifier = Modifier,
    onTextInput: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val dotColor = when (state) {
        ConnectionState.Connected -> Connected
        ConnectionState.Error -> ErrorColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (state) {
        ConnectionState.Connected -> buildString {
            append(pcName ?: "已连接")
            latencyMs?.let { append(" · ${it}ms") }
            if (packetLossPercent > 0) append(" · 丢包${packetLossPercent}%")
        }
        ConnectionState.Connecting -> "连接中"
        ConnectionState.Error -> "连接失败"
        ConnectionState.Disconnected -> "未连接"
    }

    if (landscapeCompact) {
        val lineText = buildString {
            presetName?.let {
                append(it)
                append(" · ")
            }
            append(statusText)
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 36.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, shape = MaterialTheme.shapes.small),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                lineText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showControls) {
                Spacer(modifier = Modifier.width(4.dp))
                StatusBarControls(onTextInput, onSettings, onDisconnect, compact = true)
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, shape = MaterialTheme.shapes.small),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "遥控器",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showControls) {
                StatusBarControls(onTextInput, onSettings, onDisconnect)
            }
        }
    }
    if (!landscapeCompact) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun StatusBarControls(
    onTextInput: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
    compact: Boolean = false,
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onBackground,
    )
    val buttonSize = if (compact) 32.dp else 48.dp
    val iconSize = if (compact) 18.dp else 24.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onTextInput,
            colors = iconColors,
            modifier = Modifier.size(buttonSize),
        ) {
            Icon(Icons.Default.Keyboard, contentDescription = "文本输入", modifier = Modifier.size(iconSize))
        }
        IconButton(
            onClick = onSettings,
            colors = iconColors,
            modifier = Modifier.size(buttonSize),
        ) {
            Icon(Icons.Default.Settings, contentDescription = "设置", modifier = Modifier.size(iconSize))
        }
        IconButton(
            onClick = onDisconnect,
            colors = iconColors,
            modifier = Modifier.size(buttonSize),
        ) {
            Icon(Icons.Default.LinkOff, contentDescription = "断开", modifier = Modifier.size(iconSize))
        }
    }
}
