package com.ps2gamepad

import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.ui.controller.FaceGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

/** Phase 3 analog options: max range, response curve, inversion, calibration ranges, fuzzing. */
class AnalogSettingsTest {
    private val r = 100f
    private fun x(dx: Float, s: StickSettings) = StickMath.toAxis(StickMath.process(dx, 0f, r, s).x)
    private fun y(dy: Float, s: StickSettings) = StickMath.toAxis(StickMath.process(0f, dy, r, s).y)

    @Test fun maxRangeReachesFullDeflectionEarlier() {
        val s = StickSettings(deadZone = 0.1f, maxRange = 0.8f)
        assertEquals(127, x(80f, s))
        assertEquals(127, x(95f, s))
        assertTrue(x(70f, s) < 127)
        assertEquals(127, x(100f, StickSettings(deadZone = 0.1f, maxRange = 1f)))
        assertTrue(x(80f, StickSettings(deadZone = 0.1f, maxRange = 1f)) < 127)
    }

    @Test fun responseCurveShapesOutputButKeepsEndpoints() {
        val linear = StickSettings(deadZone = 0f, curve = 1f)
        val fine = StickSettings(deadZone = 0f, curve = 2f)
        val quick = StickSettings(deadZone = 0f, curve = 0.6f)
        assertEquals(64, x(50.4f, linear))
        assertEquals(32, x(50f, fine)) // 0.5^2 = 0.25
        assertEquals(84, x(50f, quick)) // 0.5^0.6 = 0.660
        for (s in listOf(linear, fine, quick)) {
            assertEquals(0, x(0f, s)); assertEquals(127, x(100f, s)); assertEquals(-127, x(-100f, s))
        }
    }

    @Test fun curveIsMonotonicAndHasNoJumpAtDeadZone() {
        for (curve in listOf(StickSettings.MIN_CURVE, 1f, 1.5f, 3f)) {
            val s = StickSettings(deadZone = 0.15f, curve = curve)
            var prev = 0
            for (px in 0..120) {
                val v = x(px.toFloat(), s)
                assertTrue("curve $curve at $px", v >= prev)
                prev = v
            }
            assertTrue("curve $curve: 1 % past the dead zone gives at most ~10 %", x(16f, s) <= 13)
        }
    }

    @Test fun invertYFlipsOnlyY() {
        val s = StickSettings(invertY = true)
        assertEquals(127, y(-100f, s)) // finger up -> positive when inverted
        assertEquals(-127, y(100f, s))
        assertEquals(127, x(100f, s))  // X unaffected
        assertEquals(0, y(0f, s))
    }

    @Test fun neutralIsExactUnderEverySetting() {
        for (dz in listOf(0f, 0.1f, 0.4f)) for (c in listOf(0.4f, 1f, 3f)) for (inv in listOf(false, true)) {
            val v = StickMath.process(0f, 0f, r, StickSettings(deadZone = dz, curve = c, invertY = inv))
            assertEquals(0, StickMath.toAxis(v.x)); assertEquals(0, StickMath.toAxis(v.y))
        }
    }

    @Test fun fuzzNeverLeavesHidRange() {
        val rnd = java.util.Random(7)
        repeat(20_000) {
            val s = StickSettings(
                deadZone = rnd.nextFloat() * 0.6f, sensitivity = 0.1f + rnd.nextFloat() * 5f, maxRange = rnd.nextFloat() * 1.2f,
                curve = rnd.nextFloat() * 4f, invertY = rnd.nextBoolean(),
            )
            val v = StickMath.process((rnd.nextFloat() - 0.5f) * 1000f, (rnd.nextFloat() - 0.5f) * 1000f, 1f + rnd.nextFloat() * 200f, s)
            assertTrue(v.x.isFinite() && v.y.isFinite())
            assertTrue(hypot(v.x, v.y) <= 1.0001f)
            assertTrue(StickMath.toAxis(v.x) in -127..127 && StickMath.toAxis(v.y) in -127..127)
        }
    }

    @Test fun curveBelowMinimumIsClamped() {
        assertEquals(StickSettings.MIN_CURVE, StickSettings(curve = 0.1f).sanitized().curve)
    }

    @Test fun calibrationOffsetIsBounded() {
        val s = StickSettings(centerOffsetX = 5f, centerOffsetY = -5f).sanitized()
        assertEquals(StickSettings.MAX_CENTER_OFFSET, s.centerOffsetX)
        assertEquals(-StickSettings.MAX_CENTER_OFFSET, s.centerOffsetY)
    }

    @Test fun faceSpacingKeepsButtonsInsideAndNeighboursReachable() {
        for (sp in listOf(0.8f, 1f, 1.15f, 1.3f)) {
            val g = FaceGeometry.of(sp)
            assertTrue("inside box at $sp", g.offset + g.buttonRadius <= 0.5f)
            val neighbourDistance = g.offset * sqrt(2f)
            assertTrue("thumb-roll overlap at $sp", 2 * g.hitRadius > neighbourDistance)
            assertTrue("opposite buttons never overlap at $sp", 2 * g.hitRadius < 2 * g.offset)
        }
        assertEquals(0.32f, FaceGeometry.of(1f).offset, 1e-6f)
    }
}
