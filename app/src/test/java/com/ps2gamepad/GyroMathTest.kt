package com.ps2gamepad

import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.model.GyroSettings
import com.ps2gamepad.motion.GravityFusion
import com.ps2gamepad.motion.GyroEngine
import com.ps2gamepad.motion.GyroStickMapper
import com.ps2gamepad.motion.MotionSample
import com.ps2gamepad.motion.ScreenRemap
import com.ps2gamepad.motion.SensorKind
import com.ps2gamepad.motion.Tilt
import com.ps2gamepad.motion.TiltCalculator
import com.ps2gamepad.motion.TiltSmoother
import com.ps2gamepad.motion.Vec3
import com.ps2gamepad.motion.toVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Rodrigues rotation of v about unit axis k by angle a (radians). */
fun rotate(v: Vec3, k: Vec3, a: Float): Vec3 {
    val kn = k.normalized()
    return v * cos(a) + (kn cross v) * sin(a) + kn * ((kn dot v) * (1 - cos(a)))
}

/** The "up" vector after tilting a neutral hold by (xDeg = right side down, yDeg = top towards you). */
fun tiltedUp(n: Vec3, xDeg: Float, yDeg: Float): Vec3 {
    var e1 = Vec3.X - n * (Vec3.X dot n)
    if (e1.length < 0.2f) e1 = Vec3.Y - n * (Vec3.Y dot n)
    e1 = e1.normalized()
    val e2 = (n cross e1).normalized()
    val r = (e2 * -xDeg + e1 * -yDeg) * (Math.PI.toFloat() / 180f) // tilt = -(r·e)
    val ang = r.length
    return if (ang < 1e-9f) n else rotate(n, r, ang)
}

private fun deg(rad: Double) = Math.toDegrees(rad).toFloat()

class GyroTiltTest {
    // flat on the knees, a typical 45° gaming hold, and upright like a steering wheel
    private val holds = listOf(Vec3.Z, Vec3(0f, 0.7071f, 0.7071f), Vec3(0f, 0.95f, 0.31f).normalized())
    private val defaults = GyroSettings(enabled = true)

    @Test fun neutralGivesExactlyZero() {
        for (n in holds) {
            val t = TiltCalculator(n).tilt(n)
            assertEquals(Tilt.ZERO, t)
            assertEquals(0, StickMath.toAxis(GyroStickMapper.map(t, defaults).x))
        }
    }

    @Test fun tiltIsRecoveredInEveryDirectionForEveryHold() {
        for (n in holds) {
            val calc = TiltCalculator(n)
            for (dirDeg in 0 until 360 step 5) for (mag in listOf(2f, 10f, 20f, 35f)) {
                val a = Math.toRadians(dirDeg.toDouble())
                val tx = (mag * cos(a)).toFloat(); val ty = (mag * sin(a)).toFloat()
                val t = calc.tilt(tiltedUp(n, tx, ty))
                assertEquals("hold=$n dir=$dirDeg mag=$mag x", tx, t.xDeg, 0.05f)
                assertEquals("hold=$n dir=$dirDeg mag=$mag y", ty, t.yDeg, 0.05f)
            }
        }
    }

    @Test fun cardinalDirectionsMoveOnlyTheirAxis() {
        val s = defaults
        fun stick(x: Float, y: Float) = GyroStickMapper.map(Tilt(x, y), s)
        stick(15f, 0f).let { assertTrue(it.x > 0); assertEquals(0f, it.y, 1e-6f) }   // right side down -> right
        stick(-15f, 0f).let { assertTrue(it.x < 0); assertEquals(0f, it.y, 1e-6f) }  // left
        stick(0f, -15f).let { assertTrue(it.y > 0); assertEquals(0f, it.x, 1e-6f) }  // top edge away (phone points down) -> look DOWN (HID y positive)
        stick(0f, 15f).let { assertTrue(it.y < 0); assertEquals(0f, it.x, 1e-6f) }   // top edge towards you (phone points up) -> look UP
    }

    @Test fun diagonalsMoveBothAxesTogether() {
        for ((tx, ty, sx, sy) in listOf(
            // 1.7.0: up/down is mirrored (tilt y > 0 = look up = HID y < 0)
            listOf(12f, -12f, 1f, 1f), listOf(-12f, -12f, -1f, 1f), listOf(12f, 12f, 1f, -1f), listOf(-12f, 12f, -1f, -1f),
        )) {
            val v = GyroStickMapper.map(Tilt(tx, ty), defaults)
            assertTrue("($tx,$ty)", v.x * sx > 0.2f && v.y * sy > 0.2f)
            assertEquals("equal magnitude on a 45° diagonal", abs(v.x), abs(v.y), 1e-4f)
        }
    }

    @Test fun everyIntermediateAngleKeepsItsDirectionNoSnapping() {
        for (dirDeg in 0 until 360) {
            val a = Math.toRadians(dirDeg.toDouble())
            val v = GyroStickMapper.map(Tilt((15 * cos(a)).toFloat(), (15 * sin(a)).toFloat()), defaults)
            val outDir = (deg(atan2(-v.y.toDouble(), v.x.toDouble())) + 360f) % 360f // y mirrored since 1.7.0
            val diff = abs(((outDir - dirDeg + 540f) % 360f) - 180f)
            assertTrue("input $dirDeg° -> output $outDir°", diff < 0.01f)
        }
        // the spec's examples: 15°, 30°, 45°, 60° directions
        for (dirDeg in listOf(15, 30, 45, 60)) {
            val a = Math.toRadians(dirDeg.toDouble())
            val v = GyroStickMapper.map(Tilt((15 * cos(a)).toFloat(), (15 * sin(a)).toFloat()), defaults)
            assertTrue("both axes active at $dirDeg°", abs(v.x) > 0.05f && abs(v.y) > 0.05f)
        }
    }

    @Test fun moreTiltGivesMoreOutputUpToFull() {
        var prev = -1f
        for (tenth in 0..400) {
            val m = hypot(GyroStickMapper.map(Tilt(tenth / 10f, 0f), defaults).x, 0f)
            assertTrue(m >= prev); prev = m
        }
        assertEquals(1f, prev, 1e-6f)
        val slight = GyroStickMapper.map(Tilt(5f, 0f), defaults).x
        val moderate = GyroStickMapper.map(Tilt(13f, 0f), defaults).x
        assertTrue(slight in 0.05f..0.25f); assertTrue(moderate in 0.4f..0.6f)
    }

    @Test fun circularClampKeepsDiagonalsInsideTheUnitCircle() {
        val v = GyroStickMapper.map(Tilt(60f, -60f), defaults)
        assertEquals(1f, sqrt(v.x * v.x + v.y * v.y), 1e-4f)
        assertEquals(abs(v.x), abs(v.y), 1e-4f) // direction preserved (45°)
        val rnd = java.util.Random(3)
        repeat(5000) {
            val s = GyroSettings(sensitivity = 0.25f + rnd.nextFloat() * 2.75f, maxTiltDeg = 5f + rnd.nextFloat() * 55f, deadZoneDeg = rnd.nextFloat() * 10f)
            val w = GyroStickMapper.map(Tilt((rnd.nextFloat() - 0.5f) * 200f, (rnd.nextFloat() - 0.5f) * 200f), s)
            assertTrue(hypot(w.x, w.y) <= 1.0001f)
        }
    }

    @Test fun deadZoneSwallowsTremorWithoutAJump() {
        val s = defaults.copy(deadZoneDeg = 3f)
        for (t in listOf(Tilt(1f, 1f), Tilt(-2f, 1.5f), Tilt(0f, 2.9f))) {
            val v = GyroStickMapper.map(t, s)
            assertEquals(0f, v.x, 0f); assertEquals(0f, v.y, 0f)
        }
        assertTrue(GyroStickMapper.map(Tilt(3.2f, 0f), s).x < 0.02f) // just outside: tiny, not a jump
    }

    @Test fun invertAxes() {
        val n = GyroStickMapper.map(Tilt(10f, 10f), defaults)
        val ix = GyroStickMapper.map(Tilt(10f, 10f), defaults.copy(invertX = true))
        val iy = GyroStickMapper.map(Tilt(10f, 10f), defaults.copy(invertY = true))
        assertEquals(-n.x, ix.x, 1e-6f); assertEquals(n.y, ix.y, 1e-6f)
        assertEquals(n.x, iy.x, 1e-6f); assertEquals(-n.y, iy.y, 1e-6f)
    }

    @Test fun sensitivityAndMaxTilt() {
        assertTrue(GyroStickMapper.map(Tilt(10f, 0f), defaults.copy(sensitivity = 2f)).x > GyroStickMapper.map(Tilt(10f, 0f), defaults).x)
        assertEquals(1f, GyroStickMapper.map(Tilt(10f, 0f), defaults.copy(maxTiltDeg = 10f)).x, 1e-6f)
    }

    @Test fun screenRemapForBothLandscapeOrientations() {
        val v = Vec3(1f, 2f, 3f)
        assertEquals(v, ScreenRemap.toScreen(v, 0))
        assertEquals(Vec3(-2f, 1f, 3f), ScreenRemap.toScreen(v, 1))
        assertEquals(Vec3(-1f, -2f, 3f), ScreenRemap.toScreen(v, 2))
        assertEquals(Vec3(2f, -1f, 3f), ScreenRemap.toScreen(v, 3))
    }
}

class GyroFusionTest {
    private val dt = 10_000_000L // 100 Hz

    @Test fun accelerometerAloneConvergesToUp() {
        val f = GravityFusion()
        val up = Vec3(0.3f, 0.6f, 0.74f).normalized()
        repeat(300) { i -> f.onAccel(up.x * 9.81f, up.y * 9.81f, up.z * 9.81f, (i + 1) * dt) }
        assertTrue(((f.up!! - up)).length < 0.01f)
    }

    @Test fun gyroRotationIsFollowedImmediately() {
        val f = GravityFusion()
        f.onAccel(0f, 9.81f, 0f, dt) // upright
        f.onGyro(0f, 0f, 0f, dt)
        // turn counter-clockwise about z at 1 rad/s for 0.3 s, no accelerometer samples (fast motion)
        for (i in 1..30) f.onGyro(0f, 0f, 1f, dt + i * dt)
        val u = f.up!!
        assertEquals(sin(0.3f), u.x, 0.01f)
        assertEquals(cos(0.3f), u.y, 0.01f)
    }

    @Test fun gyroBiasDoesNotDriftTheNeutral() {
        val f = GravityFusion()
        val up = Vec3(0f, 0.7071f, 0.7071f)
        var t = 0L
        repeat(60_00) { // 60 s at 100 Hz, constant gyro bias 0.02 rad/s on every axis
            t += dt
            f.onGyro(0.02f, 0.02f, 0.02f, t)
            f.onAccel(up.x * 9.81f, up.y * 9.81f, up.z * 9.81f, t)
        }
        val errDeg = Math.toDegrees(kotlin.math.acos((f.up!! dot up).coerceIn(-1f, 1f).toDouble()))
        assertTrue("drift $errDeg°", errDeg < 1.5)
    }

    @Test fun shakingBarelyMovesTheEstimate() {
        val f = GravityFusion()
        val up = Vec3.Z
        var t = 0L
        repeat(100) { t += dt; f.onAccel(0f, 0f, 9.81f, t) }
        val rnd = java.util.Random(1)
        repeat(20) { // 0.2 s of hard shaking (2 g sideways jolts)
            t += dt
            f.onAccel((rnd.nextFloat() - 0.5f) * 40f, (rnd.nextFloat() - 0.5f) * 40f, 9.81f, t)
        }
        val errDeg = Math.toDegrees(kotlin.math.acos((f.up!! dot up).coerceIn(-1f, 1f).toDouble()))
        assertTrue("shake moved $errDeg°", errDeg < 3.0)
    }

    @Test fun smootherIsTimeBased() {
        val s = TiltSmoother()
        s.filter(Tilt.ZERO, 0L, 0.5f)
        var out = Tilt.ZERO
        for (i in 1..10) out = s.filter(Tilt(10f, 0f), i * dt, 0.5f) // 0.1 s, tau = 0.1 s
        assertEquals(10f * (1 - kotlin.math.exp(-1f)), out.xDeg, 0.3f)
        assertEquals(Tilt(10f, 0f), TiltSmoother().apply { filter(Tilt.ZERO, 0, 0f) }.filter(Tilt(10f, 0f), dt, 0f))
    }
}

/** The full engine fed with simulated sensor streams: the physical-joystick behaviour. */
class GyroEngineTest {
    private val dt = 10_000_000L
    private var t = 0L
    private val settings = GyroSettings(enabled = true, smoothing = 0f)
    private val neutral = Vec3(0f, 0.7071f, 0.7071f)

    /** Hold the phone at screen-space "up" vector [upScreen] for [n] samples (accel + still gyro). */
    private fun GyroEngine.hold(upScreen: Vec3, n: Int, rotation: Int = 1, onNeutral: (com.ps2gamepad.model.GyroNeutral) -> Unit = {}): com.ps2gamepad.motion.GyroReading? {
        // device vector for this screen vector (inverse of the remap)
        val d = when (rotation) { 1 -> Vec3(upScreen.y, -upScreen.x, upScreen.z); 3 -> Vec3(-upScreen.y, upScreen.x, upScreen.z); else -> upScreen }
        var last: com.ps2gamepad.motion.GyroReading? = null
        repeat(n) {
            t += dt
            onSample(MotionSample(SensorKind.GYROSCOPE, 0f, 0f, 0f, t, rotation), onNeutral)
            last = onSample(MotionSample(SensorKind.ACCELEROMETER, d.x * 9.81f, d.y * 9.81f, d.z * 9.81f, t, rotation), onNeutral) ?: last
        }
        return last
    }

    private fun engine(): GyroEngine = GyroEngine(neutral.toNeutralModel()).also { it.settings = settings }
    private fun Vec3.toNeutralModel() = com.ps2gamepad.model.GyroNeutral(x, y, z)

    @Test fun holdingATiltKeepsTheStickDisplacedThenReturnsToCentre() {
        val e = engine()
        assertEquals(0, StickMath.toAxis(e.hold(neutral, 100)!!.stick.x))
        // tilt diagonally (right side down + top away) and HOLD for 10 s
        val tilted = tiltedUp(neutral, 10f, -10f)
        val first = e.hold(tilted, 300)!!.stick
        assertTrue(first.x > 0.2f && first.y > 0.2f) // top away = look down (1.7.0)
        val after10s = e.hold(tilted, 1000)!!.stick
        assertEquals("position-based, not velocity-based: still displaced after 10 s", first.x, after10s.x, 0.01f)
        assertEquals(first.y, after10s.y, 0.01f)
        // back to the calibrated hold -> both axes exactly 0
        val back = e.hold(neutral, 300)!!.stick
        assertEquals(0, StickMath.toAxis(back.x)); assertEquals(0, StickMath.toAxis(back.y))
    }

    @Test fun recenterMakesTheCurrentHoldNeutral() {
        val e = engine()
        val other = tiltedUp(neutral, 20f, 5f)
        assertTrue(e.hold(other, 200)!!.stick.x > 0.5f)
        var saved: com.ps2gamepad.model.GyroNeutral? = null
        e.requestRecenter()
        val r = e.hold(other, 200) { saved = it }!!
        assertEquals(0, StickMath.toAxis(r.stick.x)); assertEquals(0, StickMath.toAxis(r.stick.y))
        assertTrue("new neutral reported for saving", saved != null)
    }

    @Test fun calibrationAveragesAndKeepsStickCentredMeanwhile() {
        val e = engine()
        val hold = tiltedUp(neutral, -8f, 12f)
        e.hold(hold, 100)
        var saved: com.ps2gamepad.model.GyroNeutral? = null
        e.requestCalibration()
        val during = e.hold(hold, 50)!! // 0.5 s into the 1.5 s calibration
        assertTrue(during.calibrating)
        assertEquals(0f, during.stick.x, 0f)
        val done = e.hold(hold, 150) { saved = it }!!
        assertTrue(!done.calibrating && saved != null)
        assertEquals(0, StickMath.toAxis(done.stick.x)); assertEquals(0, StickMath.toAxis(done.stick.y))
    }

    @Test fun firstUseAutoCalibrates() {
        val e = GyroEngine(null).also { it.settings = settings }
        var saved: com.ps2gamepad.model.GyroNeutral? = null
        val r = e.hold(tiltedUp(Vec3.Z, 30f, 0f), 50) { saved = it }!!
        assertTrue(saved != null)
        assertEquals(0, StickMath.toAxis(r.stick.x))
    }

    @Test fun sameMotionInBothLandscapeOrientations() {
        for (rot in listOf(1, 3)) {
            val e = engine()
            e.requestRecenter()
            e.hold(neutral, 50, rot)
            val v = e.hold(tiltedUp(neutral, 12f, 0f), 200, rot)!!.stick
            assertTrue("rotation $rot: right side down -> stick right", v.x > 0.3f && abs(v.y) < 0.02f)
        }
    }

    @Test fun sensorNoiseAtRestStaysCentred() {
        val e = engine()
        val rnd = java.util.Random(5)
        var maxAxis = 0
        repeat(1000) {
            t += dt
            e.onSample(MotionSample(SensorKind.GYROSCOPE, (rnd.nextFloat() - 0.5f) * 0.02f, (rnd.nextFloat() - 0.5f) * 0.02f, (rnd.nextFloat() - 0.5f) * 0.02f, t, 0)) {}
            val r = e.onSample(MotionSample(SensorKind.ACCELEROMETER,
                neutral.x * 9.81f + (rnd.nextFloat() - 0.5f) * 0.15f, neutral.y * 9.81f + (rnd.nextFloat() - 0.5f) * 0.15f,
                neutral.z * 9.81f + (rnd.nextFloat() - 0.5f) * 0.15f, t, 0)) {}
            if (r != null) maxAxis = maxOf(maxAxis, abs(StickMath.toAxis(r.stick.x)), abs(StickMath.toAxis(r.stick.y)))
        }
        assertEquals("phone resting on a table must not move the camera", 0, maxAxis)
    }
}
