package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.ReportSink
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.Dpad
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.Hat
import com.ps2gamepad.model.Stick
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerInputManagerTest {

    private class FakeSink : ReportSink {
        var connected = true
        val sent = mutableListOf<ByteArray>()
        override fun send(report: ByteArray, eventUptimeMs: Long): Boolean {
            if (!connected) return false
            sent += report
            return true
        }
        val last get() = HidReportManager.decode(sent.last())
    }

    private val sink = FakeSink()
    private var now = 0L
    private val m = ControllerInputManager(sink, clock = { now })

    private fun assertNeutralSent() = assertArrayEquals(HidReportManager.NEUTRAL_REPORT, sink.sent.last())

    // ---------------- A. individual buttons

    @Test fun everyButtonPressAndReleaseProducesCorrectReports() {
        for (b in ControllerButton.entries) {
            m.setButton(b, true)
            assertTrue("${b.label} pressed", sink.last.isPressed(b))
            assertEquals(b.mask, sink.last.buttons)
            m.setButton(b, false)
            assertNeutralSent()
        }
        assertEquals(ControllerButton.entries.size * 2, sink.sent.size)
    }

    @Test fun everyDpadDirection() {
        val dirs = mapOf(Dpad(up = true) to Hat.UP, Dpad(down = true) to Hat.DOWN, Dpad(left = true) to Hat.LEFT, Dpad(right = true) to Hat.RIGHT)
        for ((d, hat) in dirs) {
            m.setDpad(d)
            assertEquals(hat.value, sink.last.hat)
            m.setDpad(Dpad.NEUTRAL)
            assertNeutralSent()
        }
    }

    @Test fun dpadDiagonalAndSlide() {
        m.setDpad(Dpad(up = true)); m.setDpad(Dpad(up = true, right = true)); m.setDpad(Dpad(right = true))
        assertEquals(listOf(Hat.UP, Hat.UP_RIGHT, Hat.RIGHT).map { it.value }, sink.sent.map { HidReportManager.decode(it).hat })
    }

    // ---------------- B. release behaviour

    @Test fun holdForFiveSecondsSendsNoExtraReportsAndReleasesCleanly() {
        m.setButton(ControllerButton.CROSS, true)
        val before = sink.sent.size
        repeat(500) { now += 10; m.setButton(ControllerButton.CROSS, true) } // 5 s of repeated "still held"
        assertEquals(before, sink.sent.size)
        assertTrue(m.state.value.isPressed(ControllerButton.CROSS))
        m.setButton(ControllerButton.CROSS, false)
        assertNeutralSent()
    }

    @Test fun rapidTappingNeverLosesATransition() {
        repeat(100) {
            m.setButton(ControllerButton.CIRCLE, true)
            m.setButton(ControllerButton.CIRCLE, false)
        }
        assertEquals(200, sink.sent.size)
        sink.sent.forEachIndexed { i, r -> assertEquals(i % 2 == 0, HidReportManager.decode(r).isPressed(ControllerButton.CIRCLE)) }
        assertNeutralSent()
    }

    @Test fun releaseAllClearsEverythingAndBumpsEpoch() {
        ControllerButton.entries.forEach { m.setButton(it, true) }
        m.setDpad(Dpad(up = true, left = true))
        m.setStick(Stick.LEFT, StickVector(1f, -1f)); m.setStick(Stick.RIGHT, StickVector(-0.5f, 0.5f))
        m.reportPointers("A", 2); m.reportPointers("B", 1)
        assertEquals(3, m.activePointers.value)
        val epoch = m.resetEpoch.value
        m.releaseAll()
        assertEquals(GamepadState.NEUTRAL, m.state.value)
        assertNeutralSent()
        assertEquals(epoch + 1, m.resetEpoch.value)
        assertEquals(0, m.activePointers.value)
    }

    @Test fun releaseAllAlwaysSendsNeutralEvenIfAlreadyNeutral() {
        m.releaseAll(); m.releaseAll()
        assertEquals(2, sink.sent.size)
    }

    @Test fun unknownHostStateIsSentOnFirstChange() {
        // Before any baseline the manager does not know what the host has, so it sends.
        m.setButton(ControllerButton.SQUARE, false)
        assertEquals(1, sink.sent.size)
    }

    @Test fun duplicateStatesAreNotResent() {
        m.releaseAll() // establishes the host baseline, as on connect
        sink.sent.clear()
        m.setButton(ControllerButton.SQUARE, false) // already released
        m.setDpad(Dpad.NEUTRAL)
        m.setStick(Stick.LEFT, StickVector.ZERO)
        assertEquals(0, sink.sent.size)
    }

    // ---------------- C. analog sticks

    @Test fun stickDirectionsMapToCorrectAxes() {
        val cfg = StickSettings(deadZone = 0.1f)
        fun push(dx: Float, dy: Float) = m.setStick(Stick.LEFT, StickMath.process(dx, dy, 100f, cfg))
        push(0f, -100f); assertEquals(0 to -127, sink.last.x to sink.last.y)
        push(0f, 100f); assertEquals(0 to 127, sink.last.x to sink.last.y)
        push(-100f, 0f); assertEquals(-127 to 0, sink.last.x to sink.last.y)
        push(100f, 0f); assertEquals(127 to 0, sink.last.x to sink.last.y)
        push(-80f, -80f); assertTrue(sink.last.x < 0 && sink.last.y < 0)
        push(80f, -80f); assertTrue(sink.last.x > 0 && sink.last.y < 0)
        push(-80f, 80f); assertTrue(sink.last.x < 0 && sink.last.y > 0)
        push(80f, 80f); assertTrue(sink.last.x > 0 && sink.last.y > 0)
        push(3f, 3f); assertEquals(0 to 0, sink.last.x to sink.last.y) // dead zone = centre
        // right stick untouched the whole time
        assertEquals(0 to 0, sink.last.z to sink.last.rz)
    }

    @Test fun rightStickUsesZAndRz() {
        m.setStick(Stick.RIGHT, StickVector(1f, -1f))
        assertEquals(listOf(0, 0, 127, -127), listOf(sink.last.x, sink.last.y, sink.last.z, sink.last.rz))
    }

    @Test fun smoothTransitionProducesMonotonicReports() {
        for (i in 0..100) m.setStick(Stick.LEFT, StickVector(i / 100f, 0f))
        val xs = sink.sent.map { HidReportManager.decode(it).x }
        assertEquals(xs.sorted(), xs)
        assertEquals(127, xs.last())
        m.setStick(Stick.LEFT, StickVector.ZERO)
        assertNeutralSent()
    }

    @Test fun axisThresholdSuppressesTinyChangesButNotCentreOrEdge() {
        val coarse = ControllerInputManager(sink, axisThreshold = 4, clock = { now })
        coarse.setStick(Stick.LEFT, StickVector(0.5f, 0f)) // 64
        val n = sink.sent.size
        coarse.setStick(Stick.LEFT, StickVector(0.51f, 0f)) // 65: suppressed
        coarse.setStick(Stick.LEFT, StickVector(0.52f, 0f)) // 66: suppressed
        assertEquals(n, sink.sent.size)
        assertEquals(66, coarse.state.value.leftX) // state is still exact for the UI
        coarse.setStick(Stick.LEFT, StickVector(0.54f, 0f)) // 69 vs sent 64 -> sent
        assertEquals(n + 1, sink.sent.size)
        coarse.setStick(Stick.LEFT, StickVector.ZERO)
        assertNeutralSent()
    }

    // ---------------- D. multi-touch combinations

    @Test fun leftStickPlusCross() {
        m.setStick(Stick.LEFT, StickVector(0f, -1f))
        m.setButton(ControllerButton.CROSS, true)
        assertEquals(-127, sink.last.y); assertTrue(sink.last.isPressed(ControllerButton.CROSS))
        m.setStick(Stick.LEFT, StickVector(0.5f, -0.5f))
        assertTrue("button survives stick movement", sink.last.isPressed(ControllerButton.CROSS))
    }

    @Test fun leftStickPlusTriangleAndCircle() {
        m.setStick(Stick.LEFT, StickVector(1f, 0f))
        m.setButton(ControllerButton.TRIANGLE, true); m.setButton(ControllerButton.CIRCLE, true)
        assertEquals(ControllerButton.TRIANGLE.mask or ControllerButton.CIRCLE.mask, sink.last.buttons)
        assertEquals(127, sink.last.x)
    }

    @Test fun bothSticksSimultaneouslyPlusFaceButton() {
        m.setStick(Stick.LEFT, StickVector(-1f, 0f))
        m.setStick(Stick.RIGHT, StickVector(0f, 1f))
        m.setButton(ControllerButton.SQUARE, true)
        val d = sink.last
        assertEquals(listOf(-127, 0, 0, 127), listOf(d.x, d.y, d.z, d.rz))
        assertTrue(d.isPressed(ControllerButton.SQUARE))
        m.setStick(Stick.LEFT, StickVector.ZERO)
        assertEquals(127, sink.last.rz) // releasing one stick does not touch the other
    }

    @Test fun dpadPlusFaceButtons() {
        m.setButton(ControllerButton.CROSS, true)
        m.setDpad(Dpad(left = true))
        m.setButton(ControllerButton.SQUARE, true)
        assertEquals(Hat.LEFT.value, sink.last.hat)
        assertEquals(ControllerButton.CROSS.mask or ControllerButton.SQUARE.mask, sink.last.buttons)
        m.setDpad(Dpad.NEUTRAL)
        assertEquals(ControllerButton.CROSS.mask or ControllerButton.SQUARE.mask, sink.last.buttons)
    }

    @Test fun l1PlusR1AndManyHolds() {
        val held = listOf(ControllerButton.L1, ControllerButton.R1, ControllerButton.L2, ControllerButton.R2, ControllerButton.START)
        held.forEach { m.setButton(it, true) }
        assertEquals(held.fold(0) { a, b -> a or b.mask }, sink.last.buttons)
        m.setButton(ControllerButton.L2, false)
        assertFalse(sink.last.isPressed(ControllerButton.L2))
        assertTrue(sink.last.isPressed(ControllerButton.L1) && sink.last.isPressed(ControllerButton.R1))
    }

    // ---------------- E. connection loss

    @Test fun reportsAreDroppedWhileDisconnectedAndFullStateSentAfterReconnect() {
        m.setButton(ControllerButton.L1, true)
        sink.connected = false
        m.setButton(ControllerButton.R1, true)
        m.setStick(Stick.LEFT, StickVector(1f, 0f))
        assertEquals(1, sink.sent.size)
        assertFalse(m.lastReport.value!!.contentEquals(HidReportManager.encode(m.state.value)))
        sink.connected = true
        m.setStick(Stick.LEFT, StickVector(0.9f, 0f))
        // first report after reconnect carries the complete state
        assertTrue(sink.last.isPressed(ControllerButton.L1) && sink.last.isPressed(ControllerButton.R1))
    }

    @Test fun disconnectWhileHoldingThenReleaseAllGivesNeutralOnReconnect() {
        m.setButton(ControllerButton.CROSS, true)
        sink.connected = false
        m.releaseAll(); m.onHostLost()
        sink.connected = true
        m.releaseAll() // what the ViewModel does on reconnect
        assertNeutralSent()
        assertEquals(GamepadState.NEUTRAL, m.state.value)
    }

    @Test fun pointerCountsPerSource() {
        m.reportPointers("STICK_LEFT", 1); m.reportPointers("FACE", 2)
        assertEquals(3, m.activePointers.value)
        m.reportPointers("FACE", 0)
        assertEquals(1, m.activePointers.value)
    }
}
