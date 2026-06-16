@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yaokongqi.remote.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * 消费 Compose 提供的触摸 **历史采样点**（设备 720~3800Hz 上报时由系统批量送达）。
 * 仅使用 [positionChange] 会丢弃中间样本，导致高刷触控「断触」。
 */
object HighRateTouch {

    inline fun PointerInputChange.forEachPositionSample(onSample: (Offset) -> Unit) {
        for (sample in historical) {
            onSample(sample.position)
        }
        onSample(position)
    }

    inline fun PointerInputChange.forEachDeltaSample(
        onSample: (dx: Float, dy: Float, uptimeMillis: Long) -> Unit,
    ) {
        var prevPos = previousPosition
        for (sample in historical) {
            val dx = sample.position.x - prevPos.x
            val dy = sample.position.y - prevPos.y
            if (dx != 0f || dy != 0f) {
                onSample(dx, dy, sample.uptimeMillis)
            }
            prevPos = sample.position
        }
        val dx = position.x - prevPos.x
        val dy = position.y - prevPos.y
        if (dx != 0f || dy != 0f) {
            onSample(dx, dy, uptimeMillis)
        }
    }
}
