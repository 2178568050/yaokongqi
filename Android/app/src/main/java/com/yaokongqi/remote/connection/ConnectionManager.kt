package com.yaokongqi.remote.connection

import android.content.Context
import android.os.Build
import com.yaokongqi.remote.AppConfig
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

    @Volatile
    private var moveAccumX = 0f

    @Volatile
    private var moveAccumY = 0f

    private val _info = MutableStateFlow(ConnectionInfo())
    val info: StateFlow<ConnectionInfo> = _info.asStateFlow()

    val deviceHistory = deviceStore.history

    val savedHost: String? get() = deviceStore.lastDevice()?.host
    val savedPcName: String? get() = deviceStore.lastDevice()?.pcName

    fun hasSavedSession(): Boolean = deviceStore.lastDevice() != null

    fun pair(host: String, pin: String) {
        disconnect()
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
        activeHost = hostToSave
        val request = Request.Builder().url(AppConfig.wsUrl(host)).build()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val pairPayload = encodePair(AppConfig.APP_MAGIC, pin.trim(), deviceName)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(pairPayload)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text) { token, pcName ->
                    sessionToken = token
                    connectedPcName = pcName
                    deviceStore.upsert(hostToSave, token, pcName)
                    markConnected(pcName)
                    startHeartbeat(webSocket)
                    startQualityProbe(webSocket)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                cleanupConnection()
                _info.value = ConnectionInfo(
                    state = ConnectionState.Error,
                    message = t.message ?: "连接失败",
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasError = _info.value.state == ConnectionState.Error
                cleanupConnection()
                if (!wasError) {
                    _info.value = ConnectionInfo(state = ConnectionState.Disconnected)
                }
            }
        })
    }

    fun reconnectSaved() {
        val device = deviceStore.lastDevice() ?: return
        reconnectTo(device.host)
    }

    fun reconnectTo(host: String) {
        val device = deviceStore.device(host) ?: return
        deviceStore.setLastHost(host)
        connectWithToken(device.host, device.token, device.pcName)
    }

    private fun connectWithToken(host: String, token: String, pcName: String?) {
        disconnect()
        _info.value = ConnectionInfo(state = ConnectionState.Connecting)
        sessionToken = token
        activeHost = host
        connectedPcName = pcName

        val request = Request.Builder().url(AppConfig.wsUrl(host)).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendMeasuredPing(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = parseIncoming(text)
                when (msg.type) {
                    "pong" -> {
                        onPongReceived(msg.seq ?: 0)
                        if (_info.value.state != ConnectionState.Connected) {
                            markConnected(pcName ?: deviceStore.device(host)?.pcName)
                            startHeartbeat(webSocket)
                            startQualityProbe(webSocket)
                        }
                    }
                    "paired" -> handleMessage(text) { t, name ->
                        sessionToken = t
                        connectedPcName = name
                        deviceStore.upsert(host, t, name)
                        markConnected(name)
                        startHeartbeat(webSocket)
                        startQualityProbe(webSocket)
                    }
                    "error" -> {
                        if (msg.code == "AUTH_FAILED") {
                            deviceStore.invalidateToken(host)
                        }
                        webSocket.close(4001, msg.code)
                        cleanupConnection()
                        _info.value = ConnectionInfo(
                            state = ConnectionState.Error,
                            message = msg.msg ?: msg.code,
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                cleanupConnection()
                _info.value = ConnectionInfo(
                    state = ConnectionState.Error,
                    message = t.message ?: "连接失败",
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasError = _info.value.state == ConnectionState.Error
                cleanupConnection()
                if (!wasError) {
                    _info.value = ConnectionInfo(state = ConnectionState.Disconnected)
                }
            }
        })
    }

    private fun cleanupConnection() {
        stopHeartbeat()
        stopQualityProbe()
        stopMoveFlusher()
        sessionToken = null
        activeHost = null
        connectedPcName = null
        webSocket = null
        resetQualityStats()
    }

    private inline fun handleMessage(text: String, onPaired: (token: String, pcName: String) -> Unit) {
        val msg = parseIncoming(text)
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
        _info.value = ConnectionInfo(
            state = ConnectionState.Connected,
            pcName = pcName ?: connectedPcName,
            latencyMs = lastLatencyMs,
            packetLossPercent = computePacketLoss(),
        )
        startMoveFlusher()
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
        pendingPings[seq] = now
        trimPendingPings()
        ws.send(encodePing(seq, now))
    }

    private fun onPongReceived(seq: Int) {
        val sentAt = pendingPings.remove(seq) ?: return
        val rtt = (System.currentTimeMillis() - sentAt).toInt().coerceAtLeast(0)
        lastLatencyMs = rtt
        recordOutcome(true)
        updateQualityFields()
    }

    private fun trimPendingPings() {
        while (pendingPings.size > 20) {
            val oldest = pendingPings.keys.first()
            pendingPings.remove(oldest)
            recordOutcome(false)
        }
    }

    private fun recordOutcome(success: Boolean) {
        recentOutcomes.addLast(success)
        while (recentOutcomes.size > 20) {
            recentOutcomes.removeFirst()
        }
    }

    private fun computePacketLoss(): Int {
        if (recentOutcomes.isEmpty()) return 0
        val lost = recentOutcomes.count { !it }
        return (lost * 100 / recentOutcomes.size).coerceIn(0, 100)
    }

    private fun resetQualityStats() {
        pendingPings.clear()
        recentOutcomes.clear()
        lastLatencyMs = null
        pingSeq.set(0)
    }

    fun sendKey(vk: Int, mods: Int) {
        val token = sessionToken ?: return
        webSocket?.send(encodeKey(token, vk, mods))
    }

    fun sendCombo(vk: Int, mods: Int) {
        val token = sessionToken ?: return
        webSocket?.send(encodeCombo(token, vk, mods))
    }

    fun sendMouseMove(dx: Float, dy: Float) {
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
        val token = sessionToken ?: return
        webSocket?.send(encodeMouseClick(token, button, action))
    }

    fun sendMouseLeftClick() = sendMouseClick("left", "tap")

    fun sendMouseDoubleClick() = sendMouseClick("left", "double")

    fun sendMouseRightClick() = sendMouseClick("right", "tap")

    fun sendMouseScroll(deltaY: Int = 0, deltaX: Int = 0) {
        if (deltaY == 0 && deltaX == 0) return
        val token = sessionToken ?: return
        webSocket?.send(encodeMouseScroll(token, deltaY, deltaX))
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val token = sessionToken ?: return
        webSocket?.send(encodeTextInput(token, text))
    }

    fun sendSystemShutdown() {
        val token = sessionToken ?: return
        webSocket?.send(encodeSystem(token, "shutdown"))
    }

    fun sendVolumeUp() = sendKey(0xAF, 0)

    fun sendVolumeDown() = sendKey(0xAE, 0)

    fun disconnect() {
        webSocket?.close(1000, "user disconnect")
        cleanupConnection()
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

    private fun startHeartbeat(ws: WebSocket) {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(25_000)
                val socket = webSocket ?: break
                if (socket != ws) break
                sendMeasuredPing(socket)
            }
        }
    }

    private fun startQualityProbe(ws: WebSocket) {
        stopQualityProbe()
        qualityJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3_000)
                val socket = webSocket ?: break
                if (socket != ws) break
                sendMeasuredPing(socket)
                delay(500)
                val expired = pendingPings.entries.filter { (_, sentAt) ->
                    System.currentTimeMillis() - sentAt > 2_000
                }
                expired.forEach { (seq, _) ->
                    pendingPings.remove(seq)
                    recordOutcome(false)
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

    private companion object {
        /** 兜底刷新间隔；主要依赖位移阈值即时发送 */
        const val MOVE_FLUSH_MS = 2L
        const val MOVE_FLUSH_THRESHOLD_PX = 0.45f
    }
}
