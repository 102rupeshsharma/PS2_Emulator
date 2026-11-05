package com.ps2gamepad

import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.Dpad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class StickMathTest {
    private val r = 100f
    private val cfg = StickSettings(deadZone = 0.1f, sensitivity = 1f)

    private fun axes(dx: Float, dy: Float, c: StickSettings = cfg): Pair<Int, Int> {
        val v = StickMath.process(dx, dy, r, c)
        return StickMath.toAxis(v.x) to StickMath.toAxis(v.y)
    }

    @Test fun centreIsExactlyZero() = assertEquals(0 to 0, axes(0f, 0f))

    @Test fun cardinalDirectionsFullDeflection() {
        assertEquals(0 to -127, axes(0f, -100f))   // up   -> Y negative (HID convention)
        assertEquals(0 to 127, axes(0f, 100f))     // down -> Y positive
        assertEquals(-127 to 0, axes(-100f, 0f))   // left
        assertEquals(127 to 0, axes(100f, 0f))     // right
    }

    @Test fun diagonalsHaveCorrectSignsAndEqualMagnitude() {
        val d = 100f / sqrt(2f)
        val ul = axes(-d, -d); val ur = axes(d, -d); val dl = axes(-d, d); val dr = axes(d, d)
        assertEquals(-90 to -90, ul)
        assertEquals(90 to -90, ur)
        assertEquals(-90 to 90, dl)
        assertEquals(90 to 90, dr)
    }

    @Test fun deadZoneOutputsZeroAndHasNoJumpAtEdge() {
        assertEquals(0 to 0, axes(9.9f, 0f))
        assertEquals(0 to 0, axes(-7f, 7f)) // magnitude 9.9 < 10
        val justOutside = axes(10.5f, 0f).first
        assertTrue("small output just past the dead zone, got $justOutside", justOutside in 0..2)
    }

    @Test fun outputIsMonotonicAlongAnAxis() {
        var prev = -1
        for (px in 0..150) {
            val x = axes(px.toFloat(), 0f).first
            assertTrue(x >= prev); prev = x
        }
        assertEquals(127, prev)
    }

    @Test fun outsideTheBoundaryIsClampedKeepingDirection() {
        val (x, y) = axes(300f, -300f)
        assertEquals(90, x); assertEquals(-90, y)
        assertEquals(127 to 0, axes(10_000f, 0f))
        val v = StickMath.process(1e6f, 1e6f, r, cfg)
        assertTrue(hypot(v.x, v.y) <= 1.0001f)
    }

    @Test fun sensitivityReachesFullDeflectionEarlier() {
        val normal = axes(50f, 0f).first
        val high = axes(50f, 0f, cfg.copy(sensitivity = 2f)).first
        val low = axes(50f, 0f, cfg.copy(sensitivity = 0.5f)).first
        assertTrue(high > normal); assertTrue(low < normal)
        assertEquals(127, axes(60f, 0f, cfg.copy(sensitivity = 2f)).first)
    }

    @Test fun zeroDeadZoneIsLinear() {
        val c = StickSettings(deadZone = 0f)
        assertEquals(64, axes(50.4f, 0f, c).first)
    }

    @Test fun invalidInputsGiveZero() {
        assertEquals(StickVector.ZERO, StickMath.process(Float.NaN, 0f, r, cfg))
        assertEquals(StickVector.ZERO, StickMath.process(10f, 10f, 0f, cfg))
        assertEquals(0, StickMath.toAxis(Float.NaN))
    }

    @Test fun toAxisIsSymmetric() {
        for (i in 0..100) {
            val f = i / 100f
            assertEquals(-StickMath.toAxis(f), StickMath.toAxis(-f))
        }
        assertEquals(127, StickMath.toAxis(2f)); assertEquals(-127, StickMath.toAxis(-2f))
    }

    @Test fun invalidSettingsAreClampedNotTrusted() {
        val s = StickSettings(deadZone = 0.95f, sensitivity = 99f, maxRange = 0.1f, curve = -3f, centerOffsetX = Float.NaN).sanitized()
        assertTrue(s.deadZone <= 0.5f); assertTrue(s.sensitivity <= 3f)
        assertTrue(s.maxRange >= s.deadZone + 0.1f - 1e-6f); assertTrue(s.curve >= 0.4f)
        assertEquals(0f, s.centerOffsetX)
        // still produces valid output
        val (x, _) = axes(100f, 0f, StickSettings(deadZone = 0.95f))
        assertTrue(x in -127..127)
    }

    @Test fun axisSignificanceRules() {
        assertFalse(StickMath.axisChangedSignificantly(10, 10, 3))
        assertFalse(StickMath.axisChangedSignificantly(10, 12, 3))
        assertTrue(StickMath.axisChangedSignificantly(10, 13, 3))
        assertTrue("return to centre always sent", StickMath.axisChangedSignificantly(1, 0, 3))
        assertTrue("full deflection always sent", StickMath.axisChangedSignificantly(125, 127, 3))
    }

    // ---------------- D-pad sectors

    @Test fun dpadCardinalsAndDiagonals() {
        assertEquals(Dpad(up = true), StickMath.dpadFromOffset(0f, -80f, r))
        assertEquals(Dpad(down = true), StickMath.dpadFromOffset(0f, 80f, r))
        assertEquals(Dpad(left = true), StickMath.dpadFromOffset(-80f, 0f, r))
        assertEquals(Dpad(right = true), StickMath.dpadFromOffset(80f, 0f, r))
        assertEquals(Dpad(up = true, right = true), StickMath.dpadFromOffset(60f, -60f, r))
        assertEquals(Dpad(up = true, left = true), StickMath.dpadFromOffset(-60f, -60f, r))
        assertEquals(Dpad(down = true, right = true), StickMath.dpadFromOffset(60f, 60f, r))
        assertEquals(Dpad(down = true, left = true), StickMath.dpadFromOffset(-60f, 60f, r))
    }

    @Test fun dpadCentreIsNeutral() {
        assertEquals(Dpad.NEUTRAL, StickMath.dpadFromOffset(5f, -5f, r))
        assertEquals(Dpad.NEUTRAL, StickMath.dpadFromOffset(0f, 0f, r))
    }

    @Test fun dpadCardinalSectorIsWiderThan45Degrees() {
        // 20 degrees off vertical is still pure Up
        val a = Math.toRadians(20.0)
        assertEquals(Dpad(up = true), StickMath.dpadFromOffset((80 * Math.sin(a)).toFloat(), (-80 * Math.cos(a)).toFloat(), r))
    }

    @Test fun dpadNeverReportsOppositeDirectionsFromOneFinger() {
        for (deg in 0 until 360 step 3) {
            val a = Math.toRadians(deg.toDouble())
            val d = StickMath.dpadFromOffset((70 * Math.cos(a)).toFloat(), (70 * Math.sin(a)).toFloat(), r)
            assertFalse(d.up && d.down); assertFalse(d.left && d.right)
            assertFalse("angle $deg must press something", d.isNeutral)
        }
    }

    @Test fun combineFingers() {
        val c = StickMath.combine(listOf(Dpad(up = true), Dpad(right = true)))
        assertEquals(Dpad(up = true, right = true), c)
        assertEquals(Dpad.NEUTRAL, StickMath.combine(emptyList()))
    }
}
