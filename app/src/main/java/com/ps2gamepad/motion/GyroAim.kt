package com.ps2gamepad.motion

import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.GyroAim
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/** Small immutable 3-vector (no Android dependency, unit-testable). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Float get() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 { val l = length; return if (l < 1e-9f) this else this * (1f / l) }
    val isFinite: Boolean get() = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)
    }
}

/**
 * Sensor fusion (complementary filter) estimating "up" (opposite of gravity) in DEVICE coordinates:
 * the gyroscope rotates the estimate every sample, the accelerometer slowly pulls it back, so it
 * never drifts. Samples far from 1 g (shaking) get much less weight.
 */
class GravityFusion(private val accelTauS: Float = 0.5f) {
    var up: Vec3? = null
        private set
    private var lastGyroNs = 0L
    private var lastAccelNs = 0L

    fun onGyro(wx: Float, wy: Float, wz: Float, tNs: Long) {
        val u = up
        if (u == null || lastGyroNs == 0L) { lastGyroNs = tNs; return }
        val dt = ((tNs - lastGyroNs) / 1e9f).coerceIn(0f, 0.1f)
        lastGyroNs = tNs
        if (dt == 0f) return
        val w = Vec3(wx, wy, wz)
        if (!w.isFinite) return
        up = (u - (w cross u) * dt).normalized()
    }

    fun onAccel(ax: Float, ay: Float, az: Float, tNs: Long) {
        val a = Vec3(ax, ay, az)
        if (!a.isFinite || a.length < 1f) return
        val measured = a.normalized()
        val u = up
        if (u == null) { up = measured; lastAccelNs = tNs; return }
        val dt = if (lastAccelNs == 0L) 0.01f else ((tNs - lastAccelNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastAccelNs = tNs
        val gError = abs(a.length - EARTH_G) / EARTH_G
        val tau = if (gError > 0.15f) accelTauS * 8f else accelTauS
        val k = dt / (tau + dt)
        up = (u * (1f - k) + measured * k).normalized()
    }

    companion object { const val EARTH_G = 9.80665f }
}

/** Device -> screen coordinates for the display rotation (x right, y up, z out of the screen). */
object ScreenRemap {
    fun toScreen(v: Vec3, rotation: Int): Vec3 = when (rotation and 3) {
        1 -> Vec3(-v.y, v.x, v.z)
        2 -> Vec3(-v.x, -v.y, v.z)
        3 -> Vec3(v.y, -v.x, v.z)
        else -> v
    }
}

enum class SensorKind { GYROSCOPE, ACCELEROMETER }

/** One raw sensor event (device coordinates) plus the display rotation at that time. */
data class MotionSample(val kind: SensorKind, val x: Float, val y: Float, val z: Float, val timestampNs: Long, val rotation: Int)

/**
 * Gyro AIM for the right stick (3.1.0), built for shooters.
 *
 * The old gyro (1.3 - 2.x) turned the phone's TILT ANGLE into a stick POSITION, with a dead zone of a
 * few degrees: small aiming corrections did nothing, then the camera suddenly started moving, and
 * holding the camera still meant holding the phone at exactly the neutral angle.
 *
 * This engine works like gyro aiming on Steam / Switch: the phone's ROTATION SPEED becomes the stick
 * deflection. Turn slowly -> the camera turns slowly; stop -> the stick is centred at once. Because
 * the game turns the camera at a speed set by the stick, the camera follows the phone's movement.
 *
 * Per gyroscope sample:
 *  1. Remove the gyroscope's bias. It is learned automatically whenever the phone is held still
 *     (no calibration button needed), so a resting phone never drifts the camera.
 *  2. Split the rotation relative to gravity (works whether the phone is held flat or upright):
 *     horizontal = turning left/right + rolling, vertical = tilting the top edge towards/away.
 *  3. Tiny noise floor (well below any real hand movement), very short smoothing.
 *  4. speed -> deflection, linear: [GyroAim.FULL_SPEED_DPS] / sensitivity gives full deflection.
 *  5. **Game dead-zone fix**: games ignore small stick values (often 15-25 %), which is exactly what
 *     made slow aiming feel dead. Any real movement starts just past that dead zone, so the game's
 *     response stays linear from the very first degree per second.
 */
class GyroAimEngine {
    @Volatile var settings: GyroAim = GyroAim()
    private val fusion = GravityFusion()
    private var bias = Vec3(0f, 0f, 0f)
    private var lastGyroNs = 0L
    private var smX = 0f
    private var smY = 0f

    // automatic bias learning: average of samples while the phone is still
    private var stillSinceNs = 0L
    private var stillSum = Vec3(0f, 0f, 0f)
    private var stillN = 0

    /** Bias currently removed (°/s), for diagnostics/tests. */
    val biasDps: Float get() = Math.toDegrees(bias.length.toDouble()).toFloat()

    /** Returns the stick vector (HID convention: +x right, +y down), or null for accelerometer samples. */
    fun onSample(s: MotionSample): StickVector? {
        if (s.kind == SensorKind.ACCELEROMETER) { fusion.onAccel(s.x, s.y, s.z, s.timestampNs); return null }
        fusion.onGyro(s.x - bias.x, s.y - bias.y, s.z - bias.z, s.timestampNs)
        val dt = if (lastGyroNs == 0L) 0f else ((s.timestampNs - lastGyroNs) / 1e9f).coerceIn(0f, 0.05f)
        lastGyroNs = s.timestampNs
        learnBias(s)
        if (dt == 0f) return StickVector.ZERO

        val cfg = settings.sanitized()
        val w = ScreenRemap.toScreen(Vec3(s.x - bias.x, s.y - bias.y, s.z - bias.z), s.rotation)
        val up = fusion.up?.let { ScreenRemap.toScreen(it, s.rotation) }?.normalized() ?: Vec3.Z
        var e1 = Vec3.X - up * (Vec3.X dot up)
        if (e1.length < 0.2f) e1 = Vec3.Y - up * (Vec3.Y dot up)
        e1 = e1.normalized()
        val e2 = (up cross e1).normalized()
        val deg = (180.0 / Math.PI).toFloat()
        var h = -((w dot up) + (w dot e2)) * deg // °/s, + = turn right
        var v = (w dot e1) * deg                 // °/s, + = look down (top edge away)

        // noise floor, faded in (no jump): below NOISE_DPS nothing, full value from 2 x NOISE_DPS
        val mag = hypot(h, v)
        val gate = ((mag - NOISE_DPS) / NOISE_DPS).coerceIn(0f, 1f)
        h *= gate; v *= gate

        // Tiered smoothing: only SMALL movements (hand jitter) are smoothed; real turns pass
        // straight through, so the camera never lags or drifts on after the phone stops.
        val direct = ((hypot(h, v) - SMOOTH_FULL_DPS) / (SMOOTH_NONE_DPS - SMOOTH_FULL_DPS)).coerceIn(0f, 1f)
        val tau = cfg.smoothing * MAX_SMOOTHING_S
        val a = if (tau <= 0f) 1f else 1f - exp(-dt / tau)
        smX += (h * (1f - direct) - smX) * a; smY += (v * (1f - direct) - smY) * a
        val outX = h * direct + smX
        val outY = v * direct + smY

        // speed -> deflection (linear), circular clamp
        val full = GyroAim.FULL_SPEED_DPS / cfg.sensitivity
        var x = outX / full
        var y = outY / full * cfg.verticalScale
        if (cfg.invertX) x = -x
        if (cfg.invertY) y = -y
        val r = hypot(x, y)
        if (r < 1e-4f) return StickVector.ZERO
        // game dead-zone fix: map (0, 1] onto (dz, 1]
        val out = (cfg.gameDeadZone + (1f - cfg.gameDeadZone) * r.coerceAtMost(1f))
        return StickVector(x / r * out, y / r * out)
    }

    private fun learnBias(s: MotionSample) {
        val raw = Vec3(s.x, s.y, s.z)
        val dev = raw - bias
        if (dev.length < STILL_RAD_S) {
            if (stillSinceNs == 0L) { stillSinceNs = s.timestampNs; stillSum = Vec3(0f, 0f, 0f); stillN = 0 }
            stillSum += raw; stillN++
            if (s.timestampNs - stillSinceNs >= STILL_NS && stillN > 0) {
                val avg = stillSum * (1f / stillN)
                bias = bias * 0.5f + avg * 0.5f // converge smoothly
                stillSinceNs = s.timestampNs; stillSum = Vec3(0f, 0f, 0f); stillN = 0
            }
        } else {
            stillSinceNs = 0L
        }
    }

    companion object {
        /** Rotation speeds below this (°/s) are sensor noise and give no output (full value from 2x). */
        const val NOISE_DPS = 0.6f
        /** Smoothing time constant at smoothing = 1 (s), applied to small movements only. */
        const val MAX_SMOOTHING_S = 0.05f
        /** Movements up to this speed (°/s) are fully smoothed... */
        const val SMOOTH_FULL_DPS = 3f
        /** ...and from this speed on not smoothed at all. */
        const val SMOOTH_NONE_DPS = 10f
        /**
         * "Still" for bias learning: within ~0.6 °/s of the current bias for 0.6 s. Android's
         * gyroscope is factory-calibrated, so the leftover bias is small; a higher limit would mistake
         * slow, deliberate tracking for drift and eat it.
         */
        const val STILL_RAD_S = 0.0105f
        const val STILL_NS = 600_000_000L
    }
}
