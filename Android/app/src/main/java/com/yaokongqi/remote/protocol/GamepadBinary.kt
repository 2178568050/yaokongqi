package com.yaokongqi.remote.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 手柄二进制帧（与 PC `protocol/binary.rs` 对齐）。
 * - WebSocket binary：18 字节（会话已鉴权，不含 token）
 * - UDP：22 字节（含会话 [udp_key]）
 */
object GamepadBinary {
    const val MAGIC: Short = 0x4B59 // LE 'Y','K'
    const val VERSION: Byte = 1
    const val FLAG_UDP: Byte = 1

    const val WS_FRAME_SIZE = 18
    const val UDP_FRAME_SIZE = 22

    fun encodeWsFrame(
        seq: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int,
        lt: Int,
        rt: Int,
        buttons: Int,
    ): ByteArray {
        val buf = ByteBuffer.allocate(WS_FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(MAGIC)
        buf.put(VERSION)
        buf.put(0)
        buf.putShort(seq.toShort())
        buf.putShort(lx.toShort())
        buf.putShort(ly.toShort())
        buf.putShort(rx.toShort())
        buf.putShort(ry.toShort())
        buf.put(lt.coerceIn(0, 255).toByte())
        buf.put(rt.coerceIn(0, 255).toByte())
        buf.putShort(buttons.toShort())
        return buf.array()
    }

    fun encodeUdpFrame(
        udpKey: Int,
        seq: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int,
        lt: Int,
        rt: Int,
        buttons: Int,
    ): ByteArray {
        val buf = ByteBuffer.allocate(UDP_FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(MAGIC)
        buf.put(VERSION)
        buf.put(FLAG_UDP)
        buf.putInt(udpKey)
        buf.putShort(seq.toShort())
        buf.putShort(lx.toShort())
        buf.putShort(ly.toShort())
        buf.putShort(rx.toShort())
        buf.putShort(ry.toShort())
        buf.put(lt.coerceIn(0, 255).toByte())
        buf.put(rt.coerceIn(0, 255).toByte())
        buf.putShort(buttons.toShort())
        return buf.array()
    }
}

fun clampGamepadHz(hz: Int): Int = hz.coerceIn(60, 500)
