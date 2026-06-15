package com.yaokongqi.remote.connection

import android.content.Context
import android.content.Intent
import android.os.Build
import com.yaokongqi.remote.AppConfig
import com.yaokongqi.remote.service.RemoteService
import com.yaokongqi.remote.protocol.encodeCombo
import com.yaokongqi.remote.protocol.encodeKey
import com.yaokongqi.remote.protocol.encodeMouseClick
import com.yaokongqi.remote.protocol.encodeMouseMove
import com.yaokongqi.remote.protocol.encodeMouseScroll
import com.yaokongqi.remote.protocol.encodePair
import com.yaokongqi.remote.protocol.encodePing
import com.yaokongqi.remote.protocol.encodeSystem
import com.yaokongqi.remote.protocol.encodeTextInput
import com.yaokongqi.remote.protocol.parseIncoming
import com.yaokongqi.remote.storage.DeviceHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot
import kotlin.math.roundToInt

private fun safeParseIncoming(text: String) =
    runCatching { parseIncoming(text) }.getOrNull()

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

data class ConnectionInfo(
    val state: ConnectionState = ConnectionState.Disconnected,
    val pcName: String? = null,
    val message: String? = null,
    val latencyMs: Int? = null,
    val packetLossPercent: Int = 0,
)

class ConnectionManager(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val deviceStore = DeviceHistoryStore(context)
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var qualityJob: Job? = null
    private var moveFlushJob: Job? = null
    private var sessionToken: String? = null
    private var activeHost: String? = null
    private var connectedPcName: String? = null

    private val pingSeq = AtomicInteger(0)
    private val pendingPings = LinkedHashMap<Int, Long>()
    private val recentOutcomes = ArrayDeque<Boolean>()
    private var lastLatencyMs: Int? = null
    private var connectionGeneration = 0
    private var reconnectJob: Job? = null

    @Volatile
    private var userInitiatedDisconnect = false

    private var reconnectAttempt = 0

    @Volatile
    private var moveAccumX = 0f

    @Volatile
    private var moveAccumY = 0f

    @Volatile
    private var pcInputEnabled = true

    @Volatile
    private var showConnectionNotification = true

    private val _info = MutableStateFlow(ConnectionInfo())
    val info: StateFlow<ConnectionInfo> = _info.asStateFlow()

    val deviceHistory = deviceStore.history

    val savedHost: String? get() = deviceStore.lastDevice()?.host
    val savedPcName: String? get() = deviceStore.lastDevice()?.pcName

    fun setPcInputEnabled(enabled: Boolean) {
        pcInputEnabled = enabled
    }

    fun setShowConnectionNotification(enabled: Boolean) {
        showConnectionNotification = enabled
        if (enabled && _info.value.state == ConnectionState.Connected) {
            startConnectionService(_info.value.pcName ?: connectedPcName)
        } else if (!enabled) {
            stopConnectionService()
        }
    }

    private fun canSendPcInput(): Boolean = pcInputEnabled && sessionToken != null

    fun hasSavedSession(): Boolean {
        val device = deviceStore.lastDevice() ?: return false
        return device.token.isNotEmpty()
    }

    fun shouldAutoReconnect(): Boolean = !userInitiatedDisconnect && hasSavedSession()

    fun pair(host: String, pin: String) {
        userInitiatedDisconnect = false
        cancelScheduledReconnect()
        val gen = beginNewConnection()
        _info.value = ConnectionInfo(state = ConnectionState.Connecting)

        val parsed = AppConfig.parseHost(host)
        if (parsed.host.isEmpty()) {
            _info.value = ConnectionInfo(
                state = ConnectionState.Error,
                message = "请输入有效的 IP 地址",
            )
            return
        }

        val hostToSave = parsed.host
        val portToSave = parsed.port
        activeHost = hostToSave
        val request = Request.Builder().url(AppConfig.wsUrl(hostToSave, portToSave)).build()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val pairPayload = encodePair(AppConfig.APP_MAGIC, pin.trim(), deviceName)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != connectionGeneration) return
                webSocket.send(pairPayload)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != connectionGeneration) return
                handleMessage(text) { token, pcName ->
                    sessionToken = token
                    connectedPcName = pcName
                    deviceStore.upsert(hostToSave, token, pcName, portToSave)
                    markConnected(pcName)
                    startHeartbeat(webSocket, gen)
                    startQualityProbe(webSocket, gen)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != connectionGeneration) return
                cleanupConnection()
                _info.value = ConnectionInfo(
                    state = ConnectionState.Error,
                    message = t.message ?: "连接失败",
                )
                scheduleReconnectAfterDrop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != connectionGeneration) return
                val wasError = _info.value.state == ConnectionState.Error
                cleanupConnection()
                if (!wasError) {
                    _info.value = ConnectionInfo(state = ConnectionState.Disconnected)
                    scheduleReconnectAfterDrop()
                }
            }
        })
    }

    fun reconnectSaved() {
        userInitiatedDisconnect = false
        reconnectAttempt = 0
        cancelScheduledReconnect()
        val device = deviceStore.lastDevice() ?: return
        if (device.token.isEmpty()) return
        reconnectTo(device.host)
    }

    fun reconnectTo(host: String) {
        val device = deviceStore.device(host) ?: return
        if (device.token.isEmpty()) return
        deviceStore.setLastHost(host)
        connectWithToken(device.host, device.port, device.token, device.pcName)
    }

    private fun connectWithToken(host: String, port: Int, token: String, pcName: String?) {
        val gen = beginNewConnection()
        _info.value = ConnectionInfo(state = ConnectionState.Connecting)
        sessionToken = token
        activeHost = host
        connectedPcName = pcName

        val request = Request.Builder().url(AppConfig.wsUrl(host, port)).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != connectionGeneration) return
                sendMeasuredPing(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != connectionGeneration) return
                val msg = safeParseIncoming(text) ?: return
                when (msg.type) {
                    "pong" -> {
                        onPongReceived(msg.seq ?: 0)
                        if (_info.value.state != ConnectionState.Connected) {
                            markConnected(pcName ?: deviceStore.device(host)?.pcName)
                            startHeartbeat(webSocket, gen)
                            startQualityProbe(webSocket, gen)
                        }
                    }
                    "paired" -> handleMessage(text) { t, name ->
                        sessionToken = t
                        connectedPcName = name
                        deviceStore.upsert(host, t, name, port)
                        markConnected(name)
                        startHeartbeat(webSocket, gen)
                        startQualityProbe(webSocket, gen)
                    }
                    "error" -> {
                        if (msg.code == "AUTH_FAILED") {
                            deviceStore.invalidateToken(host)
                        }
                        webSocket.close(4001, msg.code)
                        if (gen != connectionGeneration) return
                        cleanupConnection()
                        _info.value = ConnectionInfo(
                            state = ConnectionState.Error,
                            message = msg.msg ?: msg.code,
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != connectionGeneration) return
                cleanupConnection()
                _info.value = ConnectionInfo(
                    state = ConnectionState.Error,
                    message = t.message ?: "连接失败",
                )
                scheduleReconnectAfterDrop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != connectionGeneration) return
                val wasError = _info.value.state == ConnectionState.Error
                cleanupConnection()
                if (!wasError) {
                    _info.value = ConnectionInfo(state = ConnectionState.Disconnected)
                    scheduleReconnectAfterDrop()
                }
            }
        })
    }

    private fun beginNewConnection(): Int {
        connectionGeneration++
        val gen = connectionGeneration
        webSocket?.close(1000, "reconnecting")
        cleanupConnection()
        return gen
    }

    private fun cleanupConnection() {
        stopHeartbeat()
        stopQualityProbe()
        stopMoveFlusher()
        stopConnectionService()
        sessionToken = null
        activeHost = null
        connectedPcName = null
        webSocket = null
        resetQualityStats()
    }

    private inline fun handleMessage(text: String, onPaired: (token: String, pcName: String) -> Unit) {
        val msg = safeParseIncoming(text) ?: return
        when (msg.type) {
            "paired" -> {
                val token = msg.token ?: return
                val pcName = msg.pcName ?: activeHost.orEmpty()
                onPaired(token, pcName)
            }
            "pong" -> onPongReceived(msg.seq ?: 0)
            "error" -> {
                webSocket?.close(4000, msg.code)
                cleanupConnection()
                _info.value = ConnectionInfo(
                    state = ConnectionState.Error,
                    message = msg.msg ?: msg.code,
                )
            }
        }
    }

    private fun markConnected(pcName: String?) {
        userInitiatedDisconnect = false
        reconnectAttempt = 0
        cancelScheduledReconnect()
        _info.value = ConnectionInfo(
            state = ConnectionState.Connected,
            pcName = pcName ?: connectedPcName,
            latencyMs = lastLatencyMs,
            packetLossPercent = computePacketLoss(),
        )
        startMoveFlusher()
        startConnectionService(pcName ?: connectedPcName)
    }

    private fun updateQualityFields() {
        val current = _info.value
        if (current.state == ConnectionState.Connected) {
            _info.value = current.copy(
                latencyMs = lastLatencyMs,
                packetLossPercent = computePacketLoss(),
            )
        }
    }

    private fun sendMeasuredPing(ws: WebSocket) {
        val seq = pingSeq.incrementAndGet()
        val now = System.currentTimeMillis()
        synchronized(this) {
            pendingPings[seq] = now
            trimPendingPingsLocked()
        }
        ws.send(encodePing(seq, now))
    }

    private fun onPongReceived(seq: Int) {
        val sentAt = synchronized(this) {
            pendingPings.remove(seq)
        } ?: return
        val rtt = (System.currentTimeMillis() - sentAt).toInt().coerceAtLeast(0)
        lastLatencyMs = rtt
        recordOutcome(true)
        updateQualityFields()
    }

    private fun trimPendingPingsLocked() {
        while (pendingPings.size > 20) {
            val oldest = pendingPings.keys.first()
            pendingPings.remove(oldest)
            recordOutcomeLocked(false)
        }
    }

    private fun recordOutcome(success: Boolean) {
        synchronized(this) {
            recordOutcomeLocked(success)
        }
    }

    private fun recordOutcomeLocked(success: Boolean) {
        recentOutcomes.addLast(success)
        while (recentOutcomes.size > 20) {
            recentOutcomes.removeFirst()
        }
    }

    private fun computePacketLoss(): Int {
        val outcomes = synchronized(this) { recentOutcomes.toList() }
        if (outcomes.isEmpty()) return 0
        val lost = outcomes.count { !it }
        return (lost * 100 / outcomes.size).coerceIn(0, 100)
    }

    private fun resetQualityStats() {
        synchronized(this) {
            pendingPings.clear()
            recentOutcomes.clear()
        }
        lastLatencyMs = null
        pingSeq.set(0)
    }

    fun sendKey(vk: Int, mods: Int) {
        if (!canSendPcInput()) return
        val token = sessionToken ?: return
        webSocket?.send(encodeKey(token, vk, mods))
    }

    fun sendCombo(vk: Int, mods: Int) {
        if (!canSendPcInput()) return
        val token = sessionToken ?: return
        webSocket?.send(encodeCombo(token, vk, mods))
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        if (!canSendPcInput()) return
        if (dx == 0f && dy == 0f) return
        val flushNow = synchronized(this) {
            moveAccumX += dx
            moveAccumY += dy
            hypot(moveAccumX, moveAccumY) >= MOVE_FLUSH_THRESHOLD_PX
        }
        if (flushNow) {
            flushPendingMove()
        }
    }

    fun sendMouseClick(button: String, action: String = "tap") {
        if (!canSendPcInput()) return
        val token = sessionToken ?: return
        webSocket?.send(encodeMouseClick(token, button, action))
    }

    fun sendMouseLeftClick() = sendMouseClick("left", "tap")

    fun sendMouseDoubleClick() = sendMouseClick("left", "double")

    fun sendMouseRightClick() = sendMouseClick("right", "tap")

    fun sendMouseScroll(deltaY: Int = 0, deltaX: Int = 0) {
        if (!canSendPcInput()) return
        if (deltaY == 0 && deltaX == 0) return
        val token = sessionToken ?: return
        webSocket?.send(encodeMouseScroll(token, deltaY, deltaX))
    }

    fun sendText(text: String) {
        if (!canSendPcInput()) return
        if (text.isEmpty()) return
        val token = sessionToken ?: return
        webSocket?.send(encodeTextInput(token, text))
    }

    fun sendSystemShutdown() {
        if (!canSendPcInput()) return
        val token = sessionToken ?: return
        webSocket?.send(encodeSystem(token, "shutdown"))
    }

    fun sendVolumeUp() = sendKey(0xAF, 0)

    fun sendVolumeDown() = sendKey(0xAE, 0)

    fun disconnect() {
        userInitiatedDisconnect = true
        cancelScheduledReconnect()
        beginNewConnection()
        _info.value = ConnectionInfo(state = ConnectionState.Disconnected)
    }

    fun removeDevice(host: String) {
        if (activeHost == host) disconnect()
        deviceStore.remove(host)
    }

    fun forgetAllDevices() {
        disconnect()
        deviceStore.clearAll()
    }

    private fun startHeartbeat(ws: WebSocket, gen: Int) {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(25_000)
                val socket = webSocket ?: break
                if (socket != ws || gen != connectionGeneration) break
                sendMeasuredPing(socket)
            }
        }
    }

    private fun startQualityProbe(ws: WebSocket, gen: Int) {
        stopQualityProbe()
        qualityJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3_000)
                val socket = webSocket ?: break
                if (socket != ws || gen != connectionGeneration) break
                sendMeasuredPing(socket)
                delay(500)
                val expired = synchronized(this@ConnectionManager) {
                    pendingPings.entries.filter { (_, sentAt) ->
                        System.currentTimeMillis() - sentAt > 2_000
                    }
                }
                expired.forEach { (seq, _) ->
                    synchronized(this@ConnectionManager) {
                        pendingPings.remove(seq)
                        recordOutcomeLocked(false)
                    }
                }
                updateQualityFields()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun stopQualityProbe() {
        qualityJob?.cancel()
        qualityJob = null
    }

    private fun startMoveFlusher() {
        stopMoveFlusher()
        moveFlushJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(MOVE_FLUSH_MS)
                flushPendingMove()
            }
        }
    }

    private fun stopMoveFlusher() {
        moveFlushJob?.cancel()
        moveFlushJob = null
        moveAccumX = 0f
        moveAccumY = 0f
    }

    private fun flushPendingMove() {
        if (!canSendPcInput()) return
        val token = sessionToken ?: return
        val ws = webSocket ?: return
        val (dx, dy) = synchronized(this) {
            val ix = moveAccumX.roundToInt()
            val iy = moveAccumY.roundToInt()
            moveAccumX -= ix
            moveAccumY -= iy
            ix to iy
        }
        if (dx != 0 || dy != 0) {
            ws.send(encodeMouseMove(token, dx, dy))
        }
    }

    private fun cancelScheduledReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun scheduleReconnectAfterDrop() {
        if (userInitiatedDisconnect || !hasSavedSession()) return
        if (reconnectAttempt >= MAX_AUTO_RECONNECT) return
        reconnectJob?.cancel()
        val attempt = reconnectAttempt
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(1500L + attempt * 800L)
            if (userInitiatedDisconnect || !hasSavedSession()) return@launch
            val state = _info.value.state
            if (state == ConnectionState.Connected || state == ConnectionState.Connecting) return@launch
            reconnectAttempt = attempt + 1
            reconnectSaved()
        }
    }

    private fun startConnectionService(pcName: String?) {
        if (!showConnectionNotification) return
        val label = pcName?.takeIf { it.isNotBlank() } ?: activeHost ?: "PC"
        val intent = Intent(appContext, RemoteService::class.java).apply {
            putExtra(RemoteService.EXTRA_PC_NAME, label)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
            // 部分机型未授予通知权限时仍允许连接，仅前台保活可能受限
        }
    }

    private fun stopConnectionService() {
        appContext.stopService(Intent(appContext, RemoteService::class.java))
    }

    private companion object {
        /** 兜底刷新间隔；主要依赖位移阈值即时发送 */
        const val MOVE_FLUSH_MS = 2L
        const val MOVE_FLUSH_THRESHOLD_PX = 0.45f
        const val MAX_AUTO_RECONNECT = 6
    }
}
