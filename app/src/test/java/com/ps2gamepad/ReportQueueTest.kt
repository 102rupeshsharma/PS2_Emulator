package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.PendingReport
import com.ps2gamepad.bluetooth.ReportQueue
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.GamepadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportQueueTest {
    private fun r(s: GamepadState, t: Long = 0) = PendingReport(HidReportManager.encode(s), t)
    private fun d(p: PendingReport?) = HidReportManager.decode(p!!.bytes)

    @Test fun axisOnlyUpdatesAreCoalescedToNewest() {
        val q = ReportQueue()
        for (x in 0..100) q.offer(r(GamepadState(leftX = x), x.toLong()))
        assertEquals(1, q.size)
        val p = q.poll()
        assertEquals(100, d(p).x)
        assertEquals(100L, p!!.eventUptimeMs)
    }

    @Test fun quickPressReleaseIsNeverMerged() {
        val q = ReportQueue()
        q.offer(r(GamepadState.NEUTRAL.withButton(ControllerButton.CROSS, true)))
        q.offer(r(GamepadState.NEUTRAL))
        assertEquals(2, q.size)
        assertTrue(d(q.poll()).isPressed(ControllerButton.CROSS))
        assertEquals(0, d(q.poll()).buttons)
        assertNull(q.poll())
    }

    @Test fun axisUpdatesAfterAButtonChangeMergeOnlyIntoTheLastEntry() {
        val q = ReportQueue()
        q.offer(r(GamepadState(leftX = 10)))
        q.offer(r(GamepadState(leftX = 20).withButton(ControllerButton.L1, true)))
        q.offer(r(GamepadState(leftX = 30).withButton(ControllerButton.L1, true)))
        assertEquals(2, q.size)
        assertEquals(10, d(q.poll()).x)
        val second = d(q.poll())
        assertEquals(30, second.x); assertTrue(second.isPressed(ControllerButton.L1))
    }

    @Test fun capacityKeepsNewest() {
        val q = ReportQueue(capacity = 4)
        repeat(10) { i -> q.offer(r(GamepadState(buttons = i % 2, leftX = i))) }
        assertEquals(4, q.size)
        var lastX = -1
        while (!q.isEmpty()) lastX = d(q.poll()).x
        assertEquals(9, lastX)
    }

    @Test fun clear() {
        val q = ReportQueue(); q.offer(r(GamepadState.NEUTRAL)); q.clear()
        assertTrue(q.isEmpty())
    }
}
