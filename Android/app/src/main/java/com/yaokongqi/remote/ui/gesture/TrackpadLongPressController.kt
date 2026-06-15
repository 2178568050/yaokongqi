package com.yaokongqi.remote.ui.gesture

import android.os.Handler
import android.os.Looper

/** 统一管理触控板长按任务，应用切后台/退出时可一次性取消，避免误触 PC 右键。 */
object TrackpadLongPressController {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun cancelAll() {
        generation++
        handler.removeCallbacksAndMessages(null)
    }

    fun schedule(delayMs: Long, action: () -> Unit) {
        val token = generation
        handler.postDelayed({
            if (token == generation) {
                action()
            }
        }, delayMs)
    }
}
