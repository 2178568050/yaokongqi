package com.yaokongqi.remote.ui.game

import com.yaokongqi.remote.model.AppSettings
import com.yaokongqi.remote.model.GamepadButtons
import com.yaokongqi.remote.model.GamepadSnapshot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 触摸 → 虚拟 Xbox 右摇杆瞄准。
 *
 * 每次采样将瞬时位移映射为 rx/ry；手指停住时可衰减归零（见 aimIdleDecay）。
 * 灵敏度四档：腰射 / 开镜 / 腰射开火 / 开镜开火（见设置滑条）。
 */
class GamepadInputEngine {
    var moveLx: Int = 0
        private set
    var moveLy: Int = 0
        private set

    private var aimStickRx = 0
    private var aimStickRy = 0
    private var aimTouchActive = false
    private var lastAimEventNanos = 0L
    private var aimPollHz = 120

    var buttons: Int = 0
        private set
    var leftTrigger: Int = 0
        private set
    var rightTrigger: Int = 0
        private set

    private var tacticalBumperDown = false
    private var ultimateComboDown = false

    val isAdsActive: Boolean
        get() = leftTrigger > 0

    val isFireActive: Boolean
        get() = rightTrigger > 0

    /** 右半屏或战术瞄准手势进行中（含手指按住但未产生新 delta 的间隙） */
    val isAimGestureActive: Boolean
        get() = aimTouchActive

    fun setAimPollHz(hz: Int) {
        aimPollHz = hz.coerceIn(60, 500)
    }

    fun beginAim() {
        aimTouchActive = true
        lastAimEventNanos = 0L
        aimStickRx = 0
        aimStickRy = 0
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
        moveLx = snapMoveStick(fx.roundToInt())
        moveLy = snapMoveStick(fy.roundToInt())
    }

    /**
     * @param dx dy 相对上一采样点的位移（像素）
     */
    fun addAimDelta(
        dx: Float,
        dy: Float,
        settings: AppSettings,
        eventTimeNanos: Long = System.nanoTime(),
    ) {
        if (dx == 0f && dy == 0f) return
        val mode = resolveAimMode(settings)
        addAimDeltaInternal(
            dx = dx,
            dy = dy,
            sensX = mode.sensX,
            sensY = mode.sensY,
            smoothing = settings.aimSmoothing,
            swipeAcceleration = settings.aimSwipeAcceleration * mode.accelWeight,
            linearTouch = mode.linearTouch,
            eventTimeNanos = eventTimeNanos,
        )
    }

    private fun resolveAimMode(settings: AppSettings): AimMode = when {
        isAdsActive && isFireActive -> AimMode(
            sensX = settings.fireAdsAimSensitivityX,
            sensY = settings.fireAdsAimSensitivityY,
            linearTouch = true,
            accelWeight = 0f,
        )
        isAdsActive -> AimMode(
            sensX = settings.adsAimSensitivityX,
            sensY = settings.adsAimSensitivityY,
            linearTouch = true,
            accelWeight = 0.12f,
        )
        isFireActive -> AimMode(
            sensX = settings.fireAimSensitivityX,
            sensY = settings.fireAimSensitivityY,
            linearTouch = true,
            accelWeight = 0.25f,
        )
        else -> AimMode(
            sensX = settings.aimSensitivityX,
            sensY = settings.aimSensitivityY,
            linearTouch = false,
            accelWeight = 1f,
        )
    }

    private data class AimMode(
        val sensX: Float,
        val sensY: Float,
        val linearTouch: Boolean,
        val accelWeight: Float,
    )

    private fun addAimDeltaInternal(
        dx: Float,
        dy: Float,
        sensX: Float,
        sensY: Float,
        smoothing: Float,
        swipeAcceleration: Float,
        linearTouch: Boolean,
        eventTimeNanos: Long,
    ) {
        if (dx == 0f && dy == 0f) return
        aimTouchActive = true

        val dtMs = if (lastAimEventNanos == 0L) {
            REFERENCE_FRAME_MS
        } else {
            ((eventTimeNanos - lastAimEventNanos) / 1_000_000f).coerceIn(MIN_DT_MS, MAX_DT_MS)
        }
        lastAimEventNanos = eventTimeNanos

        val timeScale = (REFERENCE_FRAME_MS / dtMs).coerceIn(TIME_SCALE_MIN, TIME_SCALE_MAX)
        var ndx = dx * timeScale
        var ndy = -dy * timeScale

        val normSpeed = hypot(ndx.toDouble(), ndy.toDouble()).toFloat()

        if (normSpeed > MAX_NORM_DELTA_PX) {
            val cap = MAX_NORM_DELTA_PX / normSpeed
            ndx *= cap
            ndy *= cap
        }

        val profile = aimTouchProfile(normSpeed, swipeAcceleration, linearTouch)
        val sx = sensX.coerceIn(AppSettings.AIM_SENS_X_MIN, AppSettings.AIM_SENS_X_MAX)
        val sy = sensY.coerceIn(AppSettings.AIM_SENS_Y_MIN, AppSettings.AIM_SENS_Y_MAX)
        val incX = ndx * sx * AIM_BASE_SCALE * profile.acceleration * profile.precisionScale
        val incY = ndy * sy * AIM_BASE_SCALE * profile.acceleration * profile.precisionScale

        // 微调跟手：小位移零平滑直接累加；大甩枪保留少量平滑避免突变
        val blend = if (normSpeed < MICRO_AIM_MAX_PX) {
            0f
        } else {
            adaptiveAimSmoothing(smoothing, normSpeed, swipeAcceleration, profile.trackMaxPx)
        }
        val (targetRx, targetRy) = aimDeflectionFromIncrement(incX, incY)
        if (blend <= 0.001f) {
            aimStickRx = targetRx
            aimStickRy = targetRy
        } else {
            aimStickRx = snapAimStick(
                (aimStickRx * blend + targetRx * (1f - blend)).roundToInt(),
            )
            aimStickRy = snapAimStick(
                (aimStickRy * blend + targetRy * (1f - blend)).roundToInt(),
            )
        }
    }

    /** 瞬时位移 → 右摇杆偏转，按住期间保持直至松手 */
    private fun aimDeflectionFromIncrement(incX: Float, incY: Float): Pair<Int, Int> {
        var nx = incX / FRAME_AIM_CAP
        var ny = incY / FRAME_AIM_CAP
        val mag = hypot(nx.toDouble(), ny.toDouble()).toFloat()
        if (mag > 1f) {
            nx /= mag
            ny /= mag
        }
        return snapAimStick((nx * 32767f).roundToInt()) to
            snapAimStick((ny * 32767f).roundToInt())
    }

    fun releaseAim() {
        aimTouchActive = false
        lastAimEventNanos = 0L
        aimStickRx = 0
        aimStickRy = 0
    }

    /**
     * 手指仍按着但无新位移时，按 tick 间隔衰减 rx/ry。
     * @return 偏转是否发生变化（需上报 PC）
     */
    fun tickAimIdleDecay(enabled: Boolean, intervalMs: Float): Boolean {
        if (!enabled || !aimTouchActive) return false
        if (aimStickRx == 0 && aimStickRy == 0) return false
        if (lastAimEventNanos == 0L) return false
        val idleMs = (System.nanoTime() - lastAimEventNanos) / 1_000_000f
        if (idleMs < AIM_IDLE_THRESHOLD_MS) return false

        val factor = AIM_IDLE_DECAY_PER_FRAME.pow((intervalMs / REFERENCE_FRAME_MS).coerceIn(0.25f, 4f))
        val prevRx = aimStickRx
        val prevRy = aimStickRy
        aimStickRx = snapAimStick((aimStickRx * factor).roundToInt())
        aimStickRy = snapAimStick((aimStickRy * factor).roundToInt())
        if (abs(aimStickRx) < AIM_IDLE_SNAP) aimStickRx = 0
        if (abs(aimStickRy) < AIM_IDLE_SNAP) aimStickRy = 0
        return prevRx != aimStickRx || prevRy != aimStickRy
    }

    fun setButton(mask: Int, pressed: Boolean) {
        buttons = if (pressed) buttons or mask else buttons and mask.inv()
    }

    fun setLeftTrigger(pressed: Boolean) {
        leftTrigger = if (pressed) 255 else 0
    }

    /** 开镜（LT）按下切换，再次按下关闭 */
    fun toggleLeftTrigger() {
        leftTrigger = if (leftTrigger > 0) 0 else 255
    }

    fun setRightTrigger(pressed: Boolean) {
        rightTrigger = if (pressed) 255 else 0
    }

    fun setTacticalBumper(pressed: Boolean) {
        tacticalBumperDown = pressed
        syncShoulderButtons()
    }

    /** 绝招：LB + RB 同按（单 RB 在 Apex 中为标点） */
    fun setUltimateCombo(pressed: Boolean) {
        ultimateComboDown = pressed
        syncShoulderButtons()
    }

    private fun syncShoulderButtons() {
        setButton(GamepadButtons.LB, tacticalBumperDown || ultimateComboDown)
        setButton(GamepadButtons.RB, ultimateComboDown)
    }

    fun reset() {
        moveLx = 0
        moveLy = 0
        aimStickRx = 0
        aimStickRy = 0
        aimTouchActive = false
        lastAimEventNanos = 0L
        buttons = 0
        leftTrigger = 0
        rightTrigger = 0
        tacticalBumperDown = false
        ultimateComboDown = false
    }

    fun tick(): GamepadSnapshot {
        aimStickRx = 0
        aimStickRy = 0
        return buildSnapshot(includePendingAim = true)
    }

    /** 按键 / 瞄准上报：含当前右摇杆偏转（按住期间维持） */
    fun currentSnapshot(): GamepadSnapshot = buildSnapshot(includePendingAim = true)

    private fun buildSnapshot(includePendingAim: Boolean): GamepadSnapshot {
        return GamepadSnapshot(
            lx = moveLx,
            ly = moveLy,
            rx = if (includePendingAim) aimStickRx else 0,
            ry = if (includePendingAim) aimStickRy else 0,
            lt = leftTrigger,
            rt = rightTrigger,
            buttons = buttons,
        )
    }

    private data class AimTouchProfile(
        val acceleration: Float,
        val precisionScale: Float,
        val trackMaxPx: Float,
    )

    /** 开镜/开火：1:1 线性；腰射环顾：可选距离加速 */
    private fun aimTouchProfile(
        normSpeedPx: Float,
        swipeAcceleration: Float,
        linearTouch: Boolean,
    ): AimTouchProfile {
        if (linearTouch || swipeAcceleration <= 0.001f) {
            return AimTouchProfile(
                acceleration = 1f,
                precisionScale = 1f,
                trackMaxPx = TRACK_AIM_MAX_PX,
            )
        }
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
                precisionScale = 1f,
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
            normSpeedPx >= 12f -> 0.18f
            normSpeedPx >= FINE_AIM_MAX_PX -> 0.28f
            else -> 0.10f
        }
        return (base * speedFactor).coerceIn(0f, 0.35f)
    }

    private fun snapMoveStick(value: Int): Int {
        val v = value.coerceIn(-32767, 32767)
        return if (abs(v) < MOVE_STICK_OUTPUT_DEADZONE) 0 else v
    }

    /** 瞄准右摇杆：无输出死区，保留微调 */
    private fun snapAimStick(value: Int): Int = value.coerceIn(-32767, 32767)

    companion object {
        private const val REFERENCE_FRAME_MS = 16.67f
        private const val MIN_DT_MS = 0.25f
        private const val MAX_DT_MS = 48f
        private const val TIME_SCALE_MIN = 0.2f
        private const val TIME_SCALE_MAX = 3.5f
        private const val AIM_BASE_SCALE = 0.024f
        private const val MAX_NORM_DELTA_PX = 58f
        private const val MICRO_AIM_MAX_PX = 14f
        private const val FINE_AIM_MAX_PX = 5.5f
        private const val TRACK_AIM_MAX_PX = 18f
        private const val FLICK_RANGE_PX = 34f
        private const val FRAME_AIM_CAP = 1.15f
        private const val MOVE_STICK_OUTPUT_DEADZONE = 140
        private const val AIM_IDLE_THRESHOLD_MS = 45f
        private const val AIM_IDLE_DECAY_PER_FRAME = 0.88f
        private const val AIM_IDLE_SNAP = 120

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
            var lx = (cos(angle) * norm * 32767).roundToInt().coerceIn(-32767, 32767)
            var ly = (-sin(angle) * norm * 32767).roundToInt().coerceIn(-32767, 32767)
            // 左前 / 右前：略增强前进半球内的侧向+前进分量，避免对角线过「圆」导致走弧不够直
            if (ly > 0 && lx != 0) {
                val boost = (1f + norm * 0.08f).coerceAtMost(1.12f)
                lx = (lx * boost).roundToInt().coerceIn(-32767, 32767)
                ly = (ly * boost).roundToInt().coerceIn(0, 32767)
            }
            return lx to ly
        }

        /** 冲刺：满前向 + 保留左/右前侧向分量 */
        fun sprintMoveStick(
            offsetX: Float,
            maxThrowPx: Float,
        ): Pair<Int, Int> {
            if (maxThrowPx <= 0f) return 0 to 32767
            val strafeNorm = (offsetX / maxThrowPx).coerceIn(-1f, 1f)
            val lx = (strafeNorm * 32767f).roundToInt().coerceIn(-32767, 32767)
            return lx to 32767
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
