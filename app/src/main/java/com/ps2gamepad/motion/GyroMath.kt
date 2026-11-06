package com.ps2gamepad.motion

import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.GyroNeutral
import com.ps2gamepad.model.GyroSettings
import com.ps2gamepad.model.StickSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
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

fun GyroNeutral.toVec() = Vec3(x, y, z)
fun Vec3.toNeutral() = normalized().let { GyroNeutral(it.x, it.y, it.z) }

/**
 * Sensor fusion (complementary filter) estimating the "up" direction (opposite of gravity) in DEVICE
 * coordinates:
 *  - Gyroscope: every sample rotates the estimate with the measured angular velocity
 *    (du/dt = -ω × u). Fast and smooth, but integration alone would drift.
 *  - Accelerometer: at rest it measures exactly "up". The estimate is pulled towards it with a
 *    time constant [accelTauS], which removes all gyro drift, so the neutral never wanders.
 *    Samples far from 1 g (shaking, bumps) get much less weight.
 */
class GravityFusion(private val accelTauS: Float = 0.5f) {
    var up: Vec3? = null
        private set
    private var lastGyroNs = 0L
    private var lastAccelNs = 0L

    fun reset() { up = null; lastGyroNs = 0L; lastAccelNs = 0L }

    fun onGyro(wx: Float, wy: Float, wz: Float, tNs: Long) {
        val u = up
        if (u == null || lastGyroNs == 0L) { lastGyroNs = tNs; return }
        val dt = ((tNs - lastGyroNs) / 1e9f).coerceIn(0f, 0.1f) // ignore gaps (pauses) instead of jumping
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
        // trust the accelerometer less while the phone is being shaken (|a| far from 1 g)
        val gError = abs(a.length - EARTH_G) / EARTH_G
        val tau = if (gError > 0.15f) accelTauS * 8f else accelTauS
        val k = dt / (tau + dt)
        up = (u * (1f - k) + measured * k).normalized()
    }

    companion object { const val EARTH_G = 9.80665f }
}

/**
 * Device -> screen coordinates for the current display rotation (x right on screen, y up on
 * screen, z out of the screen). Values follow android.view.Surface.ROTATION_*.
 */
object ScreenRemap {
    fun toScreen(v: Vec3, rotation: Int): Vec3 = when (rotation and 3) {
        1 -> Vec3(-v.y, v.x, v.z)   // ROTATION_90
        2 -> Vec3(-v.x, -v.y, v.z)  // ROTATION_180
        3 -> Vec3(v.y, -v.x, v.z)   // ROTATION_270
        else -> v                   // ROTATION_0
    }
}

/** Tilt relative to the calibrated neutral, in degrees. +x = right side down, +y = top edge towards you. */
data class Tilt(val xDeg: Float, val yDeg: Float) {
    companion object { val ZERO = Tilt(0f, 0f) }
}

/**
 * Converts the current "up" direction into a two-axis tilt relative to the neutral hold.
 *
 * The rotation from neutral to now is an axis-angle vector r (always perpendicular to the neutral
 * up direction n). It is split on two axes perpendicular to n:
 *  - e1 = screen x projected onto the plane ⟂ n  -> pitch (top edge away from / towards you)
 *  - e2 = n × e1                                 -> roll (right side down / left side down)
 * This works whatever angle the phone is held at (flat on the knees or upright like a wheel) and
 * gives a continuous 2-D vector: any direction, any intermediate angle, no snapping.
 */
class TiltCalculator(neutral: Vec3) {
    val n: Vec3 = neutral.normalized()
    private val e1: Vec3
    private val e2: Vec3

    init {
        var a = Vec3.X - n * (Vec3.X dot n)
        if (a.length < 0.2f) a = Vec3.Y - n * (Vec3.Y dot n) // phone stood on its short edge
        e1 = a.normalized()
        e2 = (n cross e1).normalized()
    }

    fun tilt(upScreen: Vec3): Tilt {
        val g = upScreen.normalized()
        val c = n cross g
        val s = c.length
        if (s < 1e-6f) return Tilt.ZERO
        val angle = atan2(s, n dot g)            // radians, 0..π
        val r = c * (angle / s)                  // axis * angle
        val deg = (180.0 / Math.PI).toFloat()
        return Tilt(xDeg = -(r dot e2) * deg, yDeg = -(r dot e1) * deg)
    }
}

/**
 * Tilt -> right-stick vector, reusing the touch sticks' maths (StickMath.process): radial dead
 * zone without a jump at its edge, linear response, sensitivity, CIRCULAR clamp (|v| <= 1, direction
 * kept), then HID convention: y positive = down. With the defaults, tilting the top edge towards
 * you (pointing the phone up) looks up, like aiming a camera.
 */
object GyroStickMapper {
    fun map(t: Tilt, s: GyroSettings): StickVector {
        val cfg = s.sanitized()
        val x = if (cfg.invertX) -t.xDeg else t.xDeg
        // 1.7.0: up/down follows the phone like a camera: top edge towards you (phone tilts up)
        // = stick UP (HID -y). The old direction was reported as reversed; Invert Y restores it.
        val y = if (cfg.invertY) t.yDeg else -t.yDeg
        return StickMath.process(
            x, y, cfg.maxTiltDeg,
            StickSettings(deadZone = cfg.deadZoneDeg / cfg.maxTiltDeg, sensitivity = cfg.sensitivity, maxRange = 1f, curve = 1f),
        )
    }
}

/** First-order low-pass on the tilt, time-based so it behaves the same at any sensor rate. */
class TiltSmoother {
    private var state: Tilt? = null
    private var lastNs = 0L

    fun reset() { state = null; lastNs = 0L }

    fun filter(t: Tilt, tNs: Long, smoothing: Float): Tilt {
        val prev = state
        val tau = smoothing.coerceIn(0f, 1f) * MAX_TAU_S
        if (prev == null || tau <= 0f) { state = t; lastNs = tNs; return t }
        val dt = ((tNs - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = tNs
        val k = 1f - exp(-dt / tau)
        val out = Tilt(prev.xDeg + (t.xDeg - prev.xDeg) * k, prev.yDeg + (t.yDeg - prev.yDeg) * k)
        state = out
        return out
    }

    companion object { const val MAX_TAU_S = 0.2f }
}

enum class SensorKind { GYROSCOPE, ACCELEROMETER }

/** One raw sensor event (device coordinates) plus the display rotation at that time. */
data class MotionSample(val kind: SensorKind, val x: Float, val y: Float, val z: Float, val timestampNs: Long, val rotation: Int)

/** Everything the gyro test screen shows for one moment. */
data class GyroReading(
    val tilt: Tilt,
    val stick: StickVector,
    val calibrated: Boolean,
    val sampleRateHz: Int,
    val calibrating: Boolean = false,
) {
    val magnitude: Float get() = sqrt(stick.x * stick.x + stick.y * stick.y)
    /** Direction in degrees, 0 = right, 90 = up, counter-clockwise (screen convention). */
    val directionDeg: Float get() = if (magnitude < 1e-4f) 0f else ((Math.toDegrees(atan2(-stick.y, stick.x).toDouble()) + 360.0) % 360.0).toFloat()

    companion object { val NONE = GyroReading(Tilt.ZERO, StickVector.ZERO, calibrated = false, sampleRateHz = 0) }
}

/**
 * The whole gyro pipeline, driven by raw samples (call from ONE thread, the sensor thread):
 * fusion -> screen coordinates -> tilt vs neutral -> smoothing -> stick vector.
 * Calibration requests from other threads are picked up on the next sample.
 */
class GyroEngine(
    initialNeutral: GyroNeutral?,
    private val calibrationDurationNs: Long = 1_500_000_000L,
) {
    private val fusion = GravityFusion()
    private val smoother = TiltSmoother()
    private var calc: TiltCalculator? = initialNeutral?.let { TiltCalculator(it.toVec()) }
    @Volatile var settings: GyroSettings = GyroSettings()

    /** Set from any thread; applied on the sensor thread. */
    @Volatile private var recenterRequested = false
    @Volatile private var calibrationRequested = false
    private var calibStartNs = 0L
    private var calibSum = Vec3(0f, 0f, 0f)
    private var calibCount = 0
    @Volatile var calibrating = false
        private set

    private var rateWindowStart = 0L
    private var rateCount = 0
    private var rate = 0

    /** Capture the current hold as neutral immediately. */
    fun requestRecenter() { recenterRequested = true }
    /** Average the hold over [calibrationDurationNs] (keep still) and use it as neutral. */
    fun requestCalibration() { calibrationRequested = true }

    fun reset() { fusion.reset(); smoother.reset(); rateWindowStart = 0L; rateCount = 0 }

    /**
     * Feeds one sample. Returns a reading (only for accelerometer/gyro samples once "up" is known),
     * and [onNewNeutral] is called when a recenter/calibration finished (to persist it).
     */
    fun onSample(s: MotionSample, onNewNeutral: (GyroNeutral) -> Unit): GyroReading? {
        when (s.kind) {
            SensorKind.GYROSCOPE -> fusion.onGyro(s.x, s.y, s.z, s.timestampNs)
            SensorKind.ACCELEROMETER -> fusion.onAccel(s.x, s.y, s.z, s.timestampNs)
        }
        val upDevice = fusion.up ?: return null
        val up = ScreenRemap.toScreen(upDevice, s.rotation)

        if (s.kind == SensorKind.GYROSCOPE) {
            if (rateWindowStart == 0L) rateWindowStart = s.timestampNs
            rateCount++
            val win = s.timestampNs - rateWindowStart
            if (win >= 1_000_000_000L) { rate = (rateCount * 1e9 / win).toInt(); rateCount = 0; rateWindowStart = s.timestampNs }
        }

        if (recenterRequested) {
            recenterRequested = false
            setNeutral(up, onNewNeutral)
        }
        if (calibrationRequested) {
            calibrationRequested = false
            calibrating = true; calibStartNs = s.timestampNs; calibSum = Vec3(0f, 0f, 0f); calibCount = 0
        }
        if (calibrating) {
            calibSum += up; calibCount++
            if (s.timestampNs - calibStartNs >= calibrationDurationNs && calibCount > 0) {
                calibrating = false
                setNeutral(calibSum * (1f / calibCount), onNewNeutral)
            }
        }
        // Never calibrated: the first hold becomes neutral automatically (can be redone any time).
        if (calc == null) setNeutral(up, onNewNeutral)

        val c = calc ?: return null
        val cfg = settings
        val tilt = smoother.filter(c.tilt(up), s.timestampNs, cfg.smoothing)
        // while calibrating, keep the stick centred (the user is finding a comfortable hold)
        val stick = if (calibrating) StickVector.ZERO else GyroStickMapper.map(tilt, cfg)
        return GyroReading(tilt, stick, calibrated = true, sampleRateHz = rate, calibrating = calibrating)
    }

    private fun setNeutral(up: Vec3, onNewNeutral: (GyroNeutral) -> Unit) {
        val n = up.toNeutral()
        calc = TiltCalculator(n.toVec())
        smoother.reset()
        onNewNeutral(n)
    }
}
