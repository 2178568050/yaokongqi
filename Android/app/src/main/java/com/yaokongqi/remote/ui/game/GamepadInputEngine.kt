package com.yaokongqi.remote.ui.game

import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.GamepadSnapshot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 触摸 → 虚拟 Xbox 摇杆。
 *
 * 瞄准采用手游 FPS 常见方案：
 * - 按帧间隔归一化 delta（不同触摸采样率手感一致）
 * - 三段式加速：微调 / 跟枪 / 甩枪
 * - 速度自适应平滑：甩枪几乎零延迟，微调滤抖
 * - 手指停滑回中 + 松手衰减回中
 */
class GamepadInputEngine {
    var moveLx: Int = 0
        private set
    var moveLy: Int = 0
        private set

    private var aimOutputX = 0f
    private var aimOutputY = 0f
    private var aimTouchActive = false
    private var ticksSinceAimDelta = 0
    private var lastAimEventNanos = 0L
    private var aimPollHz = 180

    var buttons: Int = 0
        private set
    var leftTrigger: Int = 0
        private set
    var rightTrigger: Int = 0
        private set

    val isAdsActive: Boolean
        get() = leftTrigger > 0

    fun setAimPollHz(hz: Int) {
        aimPollHz = hz.coerceIn(60, 250)
    }

    fun beginAim() {
        aimTouchActive = true
        ticksSinceAimDelta = 0
        lastAimEventNanos = 0L
        aimOutputX = 0f
        aimOutputY = 0f
    }

    fun setMoveStick(lx: Int, ly: Int, sensitivity: Float = 1f) {
        val scale = sensitivity.coerceIn(AppSettings.MOVE_SENS_MIN, AppSettings.MOVE_SENS_MAX)
        var fx = lx * scale
        var fy = ly * scale
        val mag = hypot(fx.toDouble(), fy.toDouble()).toFloat()
        if (mag > 32767f) {
            fx = fx / mag * 32767f
            fy = fy / mag * 32767f
        }
        moveLx = snapMicroStick(fx.roundToInt())
        moveLy = snapMicroStick(fy.roundToInt())
    }

    /**
     * @param dx dy 本次触摸事件相对上一采样点的位移（像素）
     */
    fun addAimDelta(
        dx: Float,
        dy: Float,
        sensX: Float,
        sensY: Float,
        smoothing: Float,
        swipeAcceleration: Float = 1f,
    ) {
        aimTouchActive = true
        ticksSinceAimDelta = 0

        val now = System.nanoTime()
        val dtMs = if (lastAimEventNanos == 0L) {
            REFERENCE_FRAME_MS
        } else {
            ((now - lastAimEventNanos) / 1_000_000f).coerceIn(MIN_DT_MS, MAX_DT_MS)
        }
        lastAimEventNanos = now

        // 归一化到 60fps 基准，避免不同机型触摸上报频率导致灵敏度漂移
        val timeScale = REFERENCE_FRAME_MS / dtMs
        // 手游 FPS：手指滑动方向 = 准心移动方向（与「拖拽画面」相反，需取反触摸 delta）
        var ndx = -dx * timeScale
        var ndy = -dy * timeScale

        val normSpeed = hypot(ndx.toDouble(), ndy.toDouble()).toFloat()
        if (normSpeed < AIM_NOISE_GATE_PX) return

        if (normSpeed > MAX_NORM_DELTA_PX) {
            val cap = MAX_NORM_DELTA_PX / normSpeed
            ndx *= cap
            ndy *= cap
        }

        val profile = aimTouchProfile(normSpeed, swipeAcceleration)
        val sx = sensX.coerceIn(AppSettings.AIM_SENS_X_MIN, AppSettings.AIM_SENS_X_MAX)
        val sy = sensY.coerceIn(AppSettings.AIM_SENS_Y_MIN, AppSettings.AIM_SENS_Y_MAX)
        val targetX = ndx * sx * AIM_BASE_SCALE * profile.acceleration * profile.precisionScale
        val targetY = ndy * sy * AIM_BASE_SCALE * profile.acceleration * profile.precisionScale

        val blend = adaptiveAimSmoothing(smoothing, normSpeed, swipeAcceleration, profile.trackMaxPx)
        if (blend <= 0.001f) {
            aimOutputX = targetX
            aimOutputY = targetY
        } else {
            aimOutputX = aimOutputX * blend + targetX * (1f - blend)
            aimOutputY = aimOutputY * blend + targetY * (1f - blend)
        }
        capAimOutput(1.0f)
    }

    fun releaseAim() {
        aimTouchActive = false
        ticksSinceAimDelta = idleTicksThreshold() + 1
        lastAimEventNanos = 0L
        // 保留当前输出，由 tick(aimDecay) 逐步衰减至回中
    }

    fun setButton(mask: Int, pressed: Boolean) {
        buttons = if (pressed) buttons or mask else buttons and mask.inv()
    }

    fun setLeftTrigger(pressed: Boolean) {
        leftTrigger = if (pressed) 255 else 0
    }

    fun setRightTrigger(pressed: Boolean) {
        rightTrigger = if (pressed) 255 else 0
    }

    fun reset() {
        moveLx = 0
        moveLy = 0
        aimOutputX = 0f
        aimOutputY = 0f
        aimTouchActive = false
        ticksSinceAimDelta = 0
        lastAimEventNanos = 0L
        buttons = 0
        leftTrigger = 0
        rightTrigger = 0
    }

    fun tick(aimDecay: Float): GamepadSnapshot {
        if (aimTouchActive) {
            ticksSinceAimDelta++
            if (ticksSinceAimDelta > idleTicksThreshold()) {
                aimOutputX = 0f
                aimOutputY = 0f
            }
        } else if (abs(aimOutputX) > 0.0008f || abs(aimOutputY) > 0.0008f) {
            val decay = aimDecay.coerceIn(0.35f, 0.92f)
            aimOutputX *= decay
            aimOutputY *= decay
            if (abs(aimOutputX) < 0.0008f) aimOutputX = 0f
            if (abs(aimOutputY) < 0.0008f) aimOutputY = 0f
        }

        val rx = snapMicroStick((aimOutputX * 32767f).roundToInt())
        val ry = snapMicroStick((aimOutputY * 32767f).roundToInt())
        return GamepadSnapshot(
            lx = moveLx,
            ly = moveLy,
            rx = rx,
            ry = ry,
            lt = leftTrigger,
            rt = rightTrigger,
            buttons = buttons,
        )
    }

    private fun idleTicksThreshold(): Int =
        (aimPollHz * 0.024f).roundToInt().coerceIn(3, 7)

    private fun capAimOutput(max: Float) {
        val mag = hypot(aimOutputX.toDouble(), aimOutputY.toDouble()).toFloat()
        if (mag > max) {
            aimOutputX = aimOutputX / mag * max
            aimOutputY = aimOutputY / mag * max
        }
    }

    private data class AimTouchProfile(
        val acceleration: Float,
        val precisionScale: Float,
        val trackMaxPx: Float,
    )

    /** 微调降速、跟枪线性、甩枪加速；[swipeAcceleration] 越大越快进入加速段 */
    private fun aimTouchProfile(normSpeedPx: Float, swipeAcceleration: Float): AimTouchProfile {
        val accel = swipeAcceleration.coerceIn(
            AppSettings.AIM_SWIPE_ACCEL_MIN,
            AppSettings.AIM_SWIPE_ACCEL_MAX,
        )
        val trackMax = TRACK_AIM_MAX_PX / (1f + accel * 0.12f).coerceIn(1f, 1.65f)
        val flickRange = FLICK_RANGE_PX / (1f + accel * 0.08f).coerceIn(1f, 1.35f)
        val flickBoost = 1.2f * accel.coerceAtLeast(0.01f)

        return when {
            normSpeedPx < FINE_AIM_MAX_PX -> AimTouchProfile(
                acceleration = 1f,
                precisionScale = 0.82f,
                trackMaxPx = trackMax,
            )
            normSpeedPx < trackMax -> {
                val t = ((normSpeedPx - FINE_AIM_MAX_PX) / (trackMax - FINE_AIM_MAX_PX))
                    .coerceIn(0f, 1f)
                AimTouchProfile(
                    acceleration = 1f + t * t * flickBoost * 0.35f,
                    precisionScale = 1f,
                    trackMaxPx = trackMax,
                )
            }
            else -> {
                val t = ((normSpeedPx - trackMax) / flickRange).coerceIn(0f, 1f)
                AimTouchProfile(
                    acceleration = 1f + t * t * flickBoost,
                    precisionScale = 1f,
                    trackMaxPx = trackMax,
                )
            }
        }
    }

    /** 甩枪少平滑、微调多平滑 */
    private fun adaptiveAimSmoothing(
        smoothing: Float,
        normSpeedPx: Float,
        swipeAcceleration: Float,
        trackMaxPx: Float,
    ): Float {
        val base = smoothing.coerceIn(0f, 0.75f)
        val accel = swipeAcceleration.coerceIn(
            AppSettings.AIM_SWIPE_ACCEL_MIN,
            AppSettings.AIM_SWIPE_ACCEL_MAX,
        )
        val speedFactor = when {
            normSpeedPx >= trackMaxPx -> (0.06f - accel * 0.012f).coerceAtLeast(0.02f)
            normSpeedPx >= 12f -> 0.22f
            normSpeedPx >= FINE_AIM_MAX_PX -> 0.42f
            else -> 0.78f
        }
        return (base * speedFactor).coerceIn(0f, 0.48f)
    }

    private fun snapMicroStick(value: Int): Int {
        val v = value.coerceIn(-32767, 32767)
        return if (abs(v) < STICK_OUTPUT_DEADZONE) 0 else v
    }

    companion object {
        private const val REFERENCE_FRAME_MS = 16.67f
        private const val MIN_DT_MS = 4f
        private const val MAX_DT_MS = 48f
        private const val AIM_BASE_SCALE = 0.023f
        private const val AIM_NOISE_GATE_PX = 0.30f
        private const val MAX_NORM_DELTA_PX = 58f
        private const val FINE_AIM_MAX_PX = 5.5f
        private const val TRACK_AIM_MAX_PX = 18f
        private const val FLICK_RANGE_PX = 34f
        private const val STICK_OUTPUT_DEADZONE = 720

        fun offsetToStickForMove(
            offsetX: Float,
            offsetY: Float,
            radius: Float,
            deadzone: Float,
        ): Pair<Int, Int> {
            if (radius <= 0f) return 0 to 0
            val mag = hypot(offsetX.toDouble(), offsetY.toDouble()).toFloat()
            val dz = deadzone.coerceIn(0f, 0.35f)
            val inner = radius * dz
            if (mag <= inner) return 0 to 0
            var norm = ((mag - inner) / (radius - inner)).coerceIn(0f, 1f)
            norm = norm.pow(0.88f)
            norm = norm * norm * (3f - 2f * norm)
            val angle = kotlin.math.atan2(offsetY, offsetX)
            val lx = (cos(angle) * norm * 32767).roundToInt().coerceIn(-32767, 32767)
            val ly = (-sin(angle) * norm * 32767).roundToInt().coerceIn(-32767, 32767)
            return lx to ly
        }

        fun offsetToStick(
            offsetX: Float,
            offsetY: Float,
            radius: Float,
            deadzone: Float,
        ): Pair<Int, Int> {
            if (radius <= 0f) return 0 to 0
            val mag = hypot(offsetX.toDouble(), offsetY.toDouble()).toFloat()
            val dz = deadzone.coerceIn(0f, 0.45f)
            val inner = radius * dz
            if (mag <= inner) return 0 to 0
            var norm = ((mag - inner) / (radius - inner)).coerceIn(0f, 1f)
            norm = norm.pow(1.12f)
            val angle = kotlin.math.atan2(offsetY, offsetX)
            val lx = (cos(angle) * norm * 32767).roundToInt().coerceIn(-32767, 32767)
            val ly = (-sin(angle) * norm * 32767).roundToInt().coerceIn(-32767, 32767)
            return lx to ly
        }
    }
}
