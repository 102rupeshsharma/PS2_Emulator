package com.ps2gamepad.pc

import com.ps2gamepad.motion.GravityFusion
import com.ps2gamepad.motion.MotionSample
import com.ps2gamepad.motion.ScreenRemap
import com.ps2gamepad.motion.SensorKind
import com.ps2gamepad.motion.Vec3
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Gyroscope mouse for PC Mode: RELATIVE movement from angular velocity (like a real mouse / Steam
 * gyro aiming), not absolute tilt.
 *
 * Per gyroscope sample:
 *  1. Subtract the calibrated gyro bias (calibration = keep still 1.5 s), so a resting phone does not drift.
 *  2. Convert to screen axes (display rotation) and split relative to the CURRENT gravity direction
 *     (sensor fusion), which makes it work however the phone is held:
 *       horizontal = turning left/right about the vertical axis + rolling left/right
 *       vertical   = tilting the top edge towards you (up) / away (down), like aiming a camera
 *  3. Dead zone on rotation speed (rescaled, no jump), smoothing, sensitivity X/Y, inversion.
 *  4. Integrate over the sample interval -> mouse counts; per-sample rate limit against spikes.
 */
class GyroMouseEngine(private val calibrationNs: Long = 1_500_000_000L) {
    @Volatile var settings: PcMouseSettings = PcMouseSettings()
    private val fusion = GravityFusion()
    private var bias = Vec3(0f, 0f, 0f)
    private var lastGyroNs = 0L
    private var smH = 0f
    private var smV = 0f

    @Volatile private var calibrationRequested = false
    @Volatile var calibrating = false; private set
    private var calibStart = 0L
    private var calibSum = Vec3(0f, 0f, 0f)
    private var calibN = 0
    @Volatile private var recenterRequested = false

    /** Keep the phone still for 1.5 s: measures the gyroscope's bias (removes slow drift). */
    fun requestCalibration() { calibrationRequested = true }
    /** New neutral: clears smoothing state so movement restarts cleanly from the current hold. */
    fun requestRecenter() { recenterRequested = true }

    val biasDps: Float get() = Math.toDegrees(bias.length.toDouble()).toFloat()

    /** Returns the mouse movement (counts) for this sample, or null for non-gyro samples / while calibrating. */
    fun onSample(s: MotionSample): Pair<Float, Float>? {
        if (s.kind == SensorKind.ACCELEROMETER) { fusion.onAccel(s.x, s.y, s.z, s.timestampNs); return null }
        fusion.onGyro(s.x - bias.x, s.y - bias.y, s.z - bias.z, s.timestampNs)

        if (calibrationRequested) {
            calibrationRequested = false
            calibrating = true; calibStart = s.timestampNs; calibSum = Vec3(0f, 0f, 0f); calibN = 0
        }
        if (calibrating) {
            calibSum += Vec3(s.x, s.y, s.z); calibN++
            if (s.timestampNs - calibStart >= calibrationNs && calibN > 0) {
                bias = calibSum * (1f / calibN); calibrating = false
            }
            lastGyroNs = s.timestampNs
            return null
        }
        if (recenterRequested) { recenterRequested = false; smH = 0f; smV = 0f }

        val dt = if (lastGyroNs == 0L) 0f else ((s.timestampNs - lastGyroNs) / 1e9f).coerceIn(0f, 0.05f)
        lastGyroNs = s.timestampNs
        if (dt == 0f) return 0f to 0f

        val cfg = settings
        val w = ScreenRemap.toScreen(Vec3(s.x - bias.x, s.y - bias.y, s.z - bias.z), s.rotation)
        val up = fusion.up?.let { ScreenRemap.toScreen(it, s.rotation) }?.normalized() ?: Vec3.Z
        var e1 = Vec3.X - up * (Vec3.X dot up)
        if (e1.length < 0.2f) e1 = Vec3.Y - up * (Vec3.Y dot up)
        e1 = e1.normalized()
        val e2 = (up cross e1).normalized()
        val deg = (180.0 / Math.PI).toFloat()
        var h = -((w dot up) + (w dot e2)) * deg     // °/s, + = right
        var v = (w dot e1) * deg                     // °/s, + = down (top edge away); 1.7.0: was reversed

        // radial dead zone on rotation speed, rescaled so there is no jump at its edge
        val mag = hypot(h, v)
        if (mag <= cfg.gyroDeadZoneDps) { h = 0f; v = 0f } else { val k = (mag - cfg.gyroDeadZoneDps) / mag; h *= k; v *= k }

        // smoothing (time-based low-pass, at most ~80 ms so aiming stays responsive)
        val tau = cfg.gyroSmoothing * 0.08f
        val a = if (tau <= 0f) 1f else 1f - exp(-dt / tau)
        smH += (h - smH) * a; smV += (v - smV) * a

        var dx = smH * dt * COUNTS_PER_DEGREE * cfg.gyroSensitivity * cfg.gyroSensitivityX
        var dy = smV * dt * COUNTS_PER_DEGREE * cfg.gyroSensitivity * cfg.gyroSensitivityY
        if (cfg.gyroInvertX) dx = -dx
        if (cfg.gyroInvertY) dy = -dy
        return dx.coerceIn(-MAX_COUNTS_PER_SAMPLE, MAX_COUNTS_PER_SAMPLE) to dy.coerceIn(-MAX_COUNTS_PER_SAMPLE, MAX_COUNTS_PER_SAMPLE)
    }

    companion object {
        /** Mouse counts per degree of rotation at sensitivity 1 (≈ pixels at Windows' default pointer speed). */
        const val COUNTS_PER_DEGREE = 25f
        /** Rate limit: no single sample may move more than this (sensor spikes, phone dropped...). */
        const val MAX_COUNTS_PER_SAMPLE = 400f
    }
}
