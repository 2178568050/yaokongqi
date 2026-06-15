package com.yaokongqi.remote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yaokongqi.remote.connection.ConnectionInfo
import com.yaokongqi.remote.connection.ConnectionManager
import com.yaokongqi.remote.connection.ConnectionState
import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.ButtonAction
import com.yaokongqi.remote.model.ButtonLayout
import com.yaokongqi.remote.model.KeyboardKey
import com.yaokongqi.remote.model.LayoutMode
import com.yaokongqi.remote.model.LayoutPreset
import com.yaokongqi.remote.model.RemoteButton
import com.yaokongqi.remote.model.ScenarioLayoutPresets
import com.yaokongqi.remote.model.SavedDevice
import com.yaokongqi.remote.model.TouchSensitivity
import com.yaokongqi.remote.storage.AppSettingsStore
import com.yaokongqi.remote.storage.ButtonLayoutStore
import com.yaokongqi.remote.storage.LayoutPresetStore
import com.yaokongqi.remote.model.GamepadLayout
import com.yaokongqi.remote.model.GamepadLayouts
import com.yaokongqi.remote.model.GamepadSnapshot
import com.yaokongqi.remote.model.RemoteInputMode
import com.yaokongqi.remote.ui.game.GamepadInputEngine
import com.yaokongqi.remote.ui.gesture.TrackpadLongPressController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val manager = ConnectionManager(app, viewModelScope)
    private val layoutStore = ButtonLayoutStore(app)
    private val presetStore = LayoutPresetStore(app)
    private val settingsStore = AppSettingsStore(app)

    val connectionInfo: StateFlow<ConnectionInfo> = manager.info
    val deviceHistory = manager.deviceHistory
    val buttonLayout = layoutStore.layout
    val layoutPresets = presetStore.collection
    val appSettings: StateFlow<AppSettings> = settingsStore.settings

    val gamepadEngine = GamepadInputEngine()
    val gamepadError = manager.gamepadError

    private var gamepadLoopJob: Job? = null
    private val _gamepadLayoutEditRequest = MutableStateFlow(0)
    val gamepadLayoutEditRequest: StateFlow<Int> = _gamepadLayoutEditRequest.asStateFlow()

    private val _minimalScrollMode = MutableStateFlow(false)
    val minimalScrollMode: StateFlow<Boolean> = _minimalScrollMode.asStateFlow()

    private val _textInputVisible = MutableStateFlow(false)
    val textInputVisible: StateFlow<Boolean> = _textInputVisible.asStateFlow()

    private val _volumeUpPressCount = MutableStateFlow(0)
    val volumeUpPressCount: StateFlow<Int> = _volumeUpPressCount.asStateFlow()

    private val _inputSessionKey = MutableStateFlow(0)
    val inputSessionKey: StateFlow<Int> = _inputSessionKey.asStateFlow()

    private val _volumeConfirmPending = MutableStateFlow(false)
    val volumeConfirmPending: StateFlow<Boolean> = _volumeConfirmPending.asStateFlow()

    private var settingsDraft: AppSettings = settingsStore.load()
    private var layoutSnapshot: ButtonLayout? = null

    val savedHost: String? get() = manager.savedHost
    val savedPcName: String? get() = manager.savedPcName
    val hasSavedSession: Boolean get() = manager.hasSavedSession()

    init {
        applyPersistedSettings()
        manager.setShowConnectionNotification(settingsStore.load().showConnectionNotification)
        presetStore.ensureBuiltinPresets()
        presetStore.ensureDefaultPreset(layoutStore.load())
        applyActivePreset()
        viewModelScope.launch {
            manager.info
                .map { it.state }
                .distinctUntilChanged()
                .collect { state ->
                    when (state) {
                        ConnectionState.Connected -> bumpInputSession()
                        ConnectionState.Disconnected, ConnectionState.Error -> resetSessionUi()
                        else -> Unit
                    }
                }
        }
        if (hasSavedSession) {
            manager.reconnectSaved()
        }
    }

    private fun applyPersistedSettings() {
        val saved = settingsStore.load()
        settingsDraft = saved
        settingsStore.updateDraft(saved)
    }

    fun isActivePresetBuiltin(): Boolean {
        val active = presetStore.collection.value.activePreset() ?: return false
        return ScenarioLayoutPresets.isBuiltin(active.id)
    }

    fun isActiveLayoutEditable(): Boolean = !isActivePresetBuiltin()

    private fun canonicalLayoutFor(preset: LayoutPreset): ButtonLayout {
        if (!ScenarioLayoutPresets.isBuiltin(preset.id)) return preset.layout
        return ScenarioLayoutPresets.all().find { it.id == preset.id }?.layout ?: preset.layout
    }

    private fun applyActivePreset() {
        val preset = presetStore.collection.value.activePreset() ?: return
        layoutStore.save(canonicalLayoutFor(preset))
        settingsStore.updateDraft(
            settingsStore.load().copy(layoutMode = canonicalLayoutFor(preset).layoutMode),
        )
    }

    fun syncDraftLayoutFromActivePreset(draft: AppSettings): AppSettings {
        val preset = presetStore.collection.value.activePreset() ?: return draft
        return draft.copy(layoutMode = canonicalLayoutFor(preset).layoutMode)
    }

    fun pair(host: String, pin: String) = manager.pair(host, pin)

    fun reconnect() = manager.reconnectSaved()

    fun reconnectTo(host: String) = manager.reconnectTo(host)

    fun disconnect() {
        manager.disconnect()
        resetVolumeCounter()
    }

    fun removeDevice(host: String) = manager.removeDevice(host)

    fun forgetAllDevices() = manager.forgetAllDevices()

    fun savedDevices(): List<SavedDevice> = deviceHistory.value.devices

    fun sendButton(button: RemoteButton) {
        when (button.action) {
            ButtonAction.KEY -> {
                if (button.mods != 0) manager.sendCombo(button.vk, button.mods)
                else manager.sendKey(button.vk, 0)
            }
            ButtonAction.VOLUME_UP -> requestVolumeUp()
            ButtonAction.VOLUME_DOWN -> {
                manager.sendVolumeDown()
                if (_volumeUpPressCount.value > 0) {
                    _volumeUpPressCount.value = _volumeUpPressCount.value - 1
                }
            }
            ButtonAction.OPEN_KEYBOARD -> showTextInput()
            ButtonAction.SHUTDOWN -> manager.sendSystemShutdown()
        }
    }

    fun requestVolumeUp() {
        if (_volumeUpPressCount.value >= VOLUME_UP_CONFIRM_THRESHOLD) {
            if (!_volumeConfirmPending.value) {
                _volumeConfirmPending.value = true
            }
            return
        }
        manager.sendVolumeUp()
        _volumeUpPressCount.value = _volumeUpPressCount.value + 1
    }

    fun confirmVolumeUp() {
        _volumeConfirmPending.value = false
        manager.sendVolumeUp()
        _volumeUpPressCount.value = _volumeUpPressCount.value + 1
    }

    fun dismissVolumeConfirm() {
        _volumeConfirmPending.value = false
    }

    fun resetVolumeCounter() {
        _volumeUpPressCount.value = 0
        _volumeConfirmPending.value = false
    }

    fun sendKeyboardKey(key: KeyboardKey) {
        if (key.mods != 0) manager.sendCombo(key.vk, key.mods)
        else manager.sendKey(key.vk, 0)
    }

    fun sendMouseMove(dx: Float, dy: Float, sensitivity: TouchSensitivity = appSettings.value.touchSensitivity) {
        manager.sendMouseMove(
            dx * sensitivity.moveMultiplier,
            dy * sensitivity.moveMultiplier,
        )
    }

    fun sendMouseLeftClick() = manager.sendMouseLeftClick()

    fun sendMouseDoubleClick() = manager.sendMouseDoubleClick()

    fun sendMouseRightClick() = manager.sendMouseRightClick()

    fun sendMouseScroll(deltaY: Int, deltaX: Int = 0, sensitivity: TouchSensitivity = appSettings.value.touchSensitivity) {
        val scale = sensitivity.scrollMultiplier / TouchSensitivity.MEDIUM.scrollMultiplier
        manager.sendMouseScroll(
            (deltaY * scale).toInt(),
            (deltaX * scale).toInt(),
        )
    }

    fun sendAltTab() = manager.sendCombo(0x09, 4)

    fun sendAltShiftTab() = manager.sendCombo(0x09, 5)

    fun sendWinTab() = manager.sendCombo(0x09, 8)

    fun sendText(text: String) = manager.sendText(text)

    fun addButton(button: RemoteButton, layoutMode: LayoutMode? = null): Boolean {
        if (isActivePresetBuiltin()) return false
        val ok = layoutStore.addButton(button, layoutMode)
        if (ok) persistActivePresetLayout()
        return ok
    }

    fun updateButton(button: RemoteButton, layoutMode: LayoutMode? = null) {
        if (isActivePresetBuiltin()) return
        layoutStore.updateButton(button, layoutMode)
        persistActivePresetLayout()
    }

    fun removeButton(id: String, layoutMode: LayoutMode? = null) {
        if (isActivePresetBuiltin()) return
        layoutStore.removeButton(id, layoutMode)
        persistActivePresetLayout()
    }

    fun resetLayout() {
        if (isActivePresetBuiltin()) return
        layoutStore.resetDefault()
        persistActivePresetLayout()
    }

    fun applyLayoutModeForEdit(mode: LayoutMode) {
        if (isActivePresetBuiltin()) return
        layoutStore.setLayoutMode(mode)
        settingsDraft = settingsDraft.copy(layoutMode = mode)
        persistActivePresetLayout()
    }

    fun saveCurrentAsPreset(name: String) {
        val layout = layoutStore.load()
        presetStore.saveCurrentLayout(name, layout)
        presetStore.updateActiveLayout(layout)
    }

    fun switchPresetNext() {
        val preset = presetStore.switchNext() ?: return
        applyPreset(preset)
    }

    fun switchPresetPrevious() {
        val preset = presetStore.switchPrevious() ?: return
        applyPreset(preset)
    }

    fun switchPresetTo(index: Int) {
        val preset = presetStore.switchTo(index) ?: return
        applyPreset(preset)
    }

    private fun applyPreset(preset: LayoutPreset) {
        val layout = canonicalLayoutFor(preset)
        layoutStore.save(layout)
        val updated = settingsStore.load().copy(layoutMode = layout.layoutMode)
        settingsStore.save(updated)
        settingsDraft = updated
    }

    fun deletePreset(id: String) {
        presetStore.deletePreset(id)
        applyActivePreset()
    }

    fun renamePreset(id: String, name: String) = presetStore.renamePreset(id, name)

    fun persistActivePresetLayout() {
        presetStore.updateActiveLayout(layoutStore.load())
    }

    fun loadSettingsForEdit(): AppSettings {
        settingsDraft = settingsStore.load().copy(layoutMode = layoutStore.load().layoutMode)
        layoutSnapshot = layoutStore.load()
        return settingsDraft
    }

    fun saveSettings(settings: AppSettings) {
        val normalized = settings.clamped()
        settingsDraft = normalized
        settingsStore.save(normalized)
        settingsStore.updateDraft(normalized)
        manager.setShowConnectionNotification(normalized.showConnectionNotification)
        if (!normalized.shooterGamepadMode) {
            exitGamepadMode()
        }
        if (isActiveLayoutEditable()) {
            layoutStore.setLayoutMode(normalized.layoutMode)
            persistActivePresetLayout()
        }
        layoutSnapshot = null
    }

    fun discardSettingsEdit() {
        val saved = settingsStore.load()
        settingsDraft = saved
        settingsStore.updateDraft(saved)
        layoutSnapshot?.let { layoutStore.save(it) }
        layoutSnapshot = null
    }

    fun showTextInput() {
        if (connectionInfo.value.state == ConnectionState.Connected) {
            _textInputVisible.value = true
        }
    }

    fun hideTextInput() {
        _textInputVisible.value = false
    }

    fun enterMinimalScrollMode() {
        if (connectionInfo.value.state == ConnectionState.Connected) {
            _minimalScrollMode.value = true
        }
    }

    fun exitMinimalScrollMode() {
        _minimalScrollMode.value = false
    }

    fun onAppForeground() {
        manager.setPcInputEnabled(true)
        when (connectionInfo.value.state) {
            ConnectionState.Connected -> bumpInputSession()
            ConnectionState.Connecting -> Unit
            ConnectionState.Disconnected, ConnectionState.Error -> {
                if (manager.shouldAutoReconnect()) {
                    manager.requestAutoReconnect()
                }
            }
        }
    }

    fun onAppBackground() {
        manager.setPcInputEnabled(false)
        gamepadEngine.reset()
        manager.updateGamepadSnapshot(gamepadEngine.tick(appSettings.value.aimDecay))
        TrackpadLongPressController.cancelAll()
        bumpInputSession()
    }

    fun enterGamepadMode() {
        if (connectionInfo.value.state != ConnectionState.Connected) return
        val hz = appSettings.value.gamepadPollHz
        manager.setRemoteInputMode(RemoteInputMode.GAMEPAD, hz)
    }

    /** 连接恢复后重新通知 PC 进入手柄模式（断联期间 UI 仍可编辑布局） */
    fun syncGamepadModeIfConnected() {
        if (connectionInfo.value.state == ConnectionState.Connected &&
            appSettings.value.shooterGamepadMode
        ) {
            enterGamepadMode()
        }
    }

    fun exitGamepadMode() {
        gamepadLoopJob?.cancel()
        gamepadLoopJob = null
        gamepadEngine.reset()
        manager.updateGamepadSnapshot(GamepadSnapshot())
        manager.setRemoteInputMode(RemoteInputMode.KEYBOARD_MOUSE)
    }

    fun runGamepadLoop(pollHz: Int, aimDecay: Float) {
        gamepadLoopJob?.cancel()
        gamepadLoopJob = viewModelScope.launch {
            val intervalMs = (1000.0 / pollHz.coerceIn(60, 250)).coerceAtLeast(4.0).toLong()
            while (isActive) {
                if (connectionInfo.value.state != ConnectionState.Connected) break
                val snapshot = gamepadEngine.tick(aimDecay)
                manager.updateGamepadSnapshot(snapshot)
                delay(intervalMs)
            }
        }
    }

    fun clearGamepadError() = manager.clearGamepadError()

    fun saveGamepadLayout(layout: GamepadLayout) {
        val updated = settingsStore.load().copy(gamepadLayout = layout)
        settingsStore.save(updated)
        settingsStore.updateDraft(updated)
        settingsDraft = updated
    }

    fun resetGamepadLayout(settings: AppSettings): AppSettings {
        val updated = settings.copy(gamepadLayout = GamepadLayouts.default())
        saveSettings(updated)
        return updated
    }

    fun requestGamepadLayoutEdit() {
        _gamepadLayoutEditRequest.value = _gamepadLayoutEditRequest.value + 1
    }

    fun resetSessionUi() {
        _minimalScrollMode.value = false
        _textInputVisible.value = false
        resetVolumeCounter()
        exitGamepadMode()
        bumpInputSession()
    }

    private fun bumpInputSession() {
        _inputSessionKey.value = _inputSessionKey.value + 1
    }

    private companion object {
        const val VOLUME_UP_CONFIRM_THRESHOLD = 10
    }
}
