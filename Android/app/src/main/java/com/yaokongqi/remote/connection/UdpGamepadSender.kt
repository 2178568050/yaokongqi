package com.yaokongqi.remote.connection

import com.yaokongqi.remote.model.GamepadSnapshot
import com.yaokongqi.remote.protocol.GamepadBinary
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/** 手柄状态 UDP 发送（最新帧覆盖，无重传）。 */
class UdpGamepadSender {
    private var socket: DatagramSocket? = null
    private var host: String? = null
    private var port: Int = 0
    private var udpKey: Int = 0
    private val seq = AtomicInteger(0)

    @Volatile
    private var configured = false

    fun configure(host: String, port: Int, udpKey: Int) {
        close()
        if (udpKey == 0 || port <= 0 || host.isBlank()) {
            configured = false
            return
        }
        this.host = host
        this.port = port
        this.udpKey = udpKey
        socket = DatagramSocket()
        configured = true
    }

    fun isReady(): Boolean = configured && socket != null

    fun send(snapshot: GamepadSnapshot): Boolean {
        if (!configured) return false
        val sock = socket ?: return false
        val targetHost = host ?: return false
        return try {
            val payload = GamepadBinary.encodeUdpFrame(
                udpKey = udpKey,
                seq = seq.incrementAndGet() and 0xFFFF,
                lx = snapshot.lx,
                ly = snapshot.ly,
                rx = snapshot.rx,
                ry = snapshot.ry,
                lt = snapshot.lt,
                rt = snapshot.rt,
                buttons = snapshot.buttons,
            )
            val address = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(payload, payload.size, address, port)
            sock.send(packet)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun close() {
        configured = false
        host = null
        port = 0
        udpKey = 0
        seq.set(0)
        runCatching { socket?.close() }
        socket = null
    }
}
