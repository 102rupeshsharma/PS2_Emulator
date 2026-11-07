package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.PendingReport
import com.ps2gamepad.bluetooth.ReportQueue
import com.ps2gamepad.bluetooth.SendScheduler
import com.ps2gamepad.bluetooth.SendStep
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.GamepadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Play-testing" the Bluetooth path (3.2.0) with a simulated link to the PC.
 *
 * The phone hands reports to Android's Bluetooth stack, which queues them and sends them over the
 * radio link. The link carries only so many reports per second (it depends on the PC and the
 * Bluetooth power mode); whatever is handed over faster waits in the stack's queue. A simulated
 * player walks with the left stick (touch events at 240 Hz) and then lifts the thumb; we measure how
 * long the PC keeps seeing "walking" after the lift.
 *
 * This models the most likely cause of "player keeps moving 1-2 s after I let go". It is a
 * simulation, not a measurement on the phone.
 */
class SendSchedulerTest {
    private fun report(s: GamepadState) = HidReportManager.encode(s)

    /** The Bluetooth link: a FIFO that delivers one report every [linkMs] ms. */
    private class Link(val linkMs: Double) {
        val queue = ArrayDeque<Pair<Long, ByteArray>>() // (handed over at, bytes)
        var nextFree = 0.0
        val delivered = mutableListOf<Pair<Double, ByteArray>>() // (arrived at PC, bytes)
        fun hand(now: Long, r: ByteArray) { queue.addLast(now to r) }
        fun tick(now: Long) {
            while (queue.isNotEmpty()) {
                val start = maxOf(nextFree, queue.first().first.toDouble())
                if (start > now) return
                val (_, r) = queue.removeFirst()
                nextFree = start + linkMs
                delivered += nextFree to r
            }
        }
    }

    private interface Sender { fun offer(r: ByteArray, now: Long); fun tick(now: Long, link: Link) }

    /** The 3.1 sender: one report every [paceMs] ms, movement coalesced only while waiting. */
    private class OldSender(val paceMs: Long) : Sender {
        val q = ReportQueue(); var last = Long.MIN_VALUE / 2
        override fun offer(r: ByteArray, now: Long) = q.offer(PendingReport(r, now))
        override fun tick(now: Long, link: Link) {
            if (!q.isEmpty() && now - last >= paceMs) { link.hand(now, q.poll()!!.bytes); last = now }
        }
    }

    /** The 3.2 sender: [SendScheduler]. */
    private class NewSender : Sender {
        val q = ReportQueue(); val s = SendScheduler()
        override fun offer(r: ByteArray, now: Long) = q.offer(PendingReport(r, now))
        override fun tick(now: Long, link: Link) {
            val step = s.next(q, now)
            if (step is SendStep.Send) { link.hand(now, step.report.bytes); s.onSent(step, true, now) }
        }
    }

    /**
     * Walk in a slowly turning circle for [walkMs], then lift the thumb.
     * Returns ms between the lift and the PC receiving a centred stick for good.
     */
    private fun walkThenStop(sender: Sender, linkMs: Double, walkMs: Long = 3000): Double {
        val link = Link(linkMs)
        var lastState: GamepadState? = null
        var liftAt = -1L
        for (t in 0L..(walkMs + 3000)) {
            if (t <= walkMs && t % 4 == 0L) { // touch events ~240 Hz
                val a = t / 600.0
                val st = GamepadState(leftX = (cos(a) * 110).toInt(), leftY = (sin(a) * 110).toInt())
                if (st != lastState) { sender.offer(report(st), t); lastState = st }
            }
            if (t == walkMs + 1) { liftAt = t; sender.offer(report(GamepadState.NEUTRAL), t) }
            sender.tick(t, link)
            link.tick(t)
        }
        val lastMoving = link.delivered.lastOrNull { HidReportManager.decode(it.second).x != 0 }?.first ?: return 0.0
        return lastMoving - liftAt
    }

    @Test fun oldSenderOverfillsASlowLinkAndThePlayerKeepsWalking() {
        // 125 Hz (battery saver, the default) into a link that carries ~89 reports/s
        val delay = walkThenStop(OldSender(8), linkMs = 11.25)
        println("PLAY | old sender, 3 s walk, slow link | still walking ${"%.0f".format(delay)} ms after the thumb lifted")
        assertTrue("reproduces the bug: $delay ms", delay > 500)
    }

    @Test fun newSenderStopsAtOnceOnTheSameLink() {
        for (linkMs in listOf(4.0, 8.0, 11.25)) {
            val delay = walkThenStop(NewSender(), linkMs)
            println("PLAY | new sender, 3 s walk, link ${"%.1f".format(1000 / linkMs)} reports/s | stopped ${"%.0f".format(delay)} ms after the lift")
            assertTrue("link $linkMs ms: stopped after $delay ms", delay < 40)
        }
    }

    @Test fun buttonPressAndReleaseDuringMovementBothGoOutAtOnceInOrder() {
        val q = ReportQueue(); val s = SendScheduler()
        val sent = mutableListOf<Pair<Long, GamepadState>>()
        fun run(from: Long, to: Long) {
            for (t in from..to) { val st = s.next(q, t); if (st is SendStep.Send) { sent += t to decodeState(st.report.bytes); s.onSent(st, true, t) } }
        }
        var moving = GamepadState(leftX = 50)
        q.offer(PendingReport(report(moving), 0)); run(0, 0)
        moving = moving.copy(leftX = 60); q.offer(PendingReport(report(moving), 2))
        val pressed = moving.withButton(ControllerButton.CROSS, true)
        q.offer(PendingReport(report(pressed), 3))
        run(1, 6)
        val released = pressed.withButton(ControllerButton.CROSS, false)
        q.offer(PendingReport(report(released), 10))
        run(7, 14)
        val crossTimes = sent.filter { it.second.isPressed(ControllerButton.CROSS) }.map { it.first }
        val releaseTime = sent.first { it.first > 6 && !it.second.isPressed(ControllerButton.CROSS) }.first
        assertEquals("press goes out within 4 ms, ahead of stale movement", 4L, crossTimes.first())
        assertTrue("release goes out at once: $releaseTime", releaseTime <= 14)
    }

    @Test fun aStopIsConfirmedTwiceSoADroppedReportCannotLeaveTheStickHeld() {
        val q = ReportQueue(); val s = SendScheduler()
        val neutral = report(GamepadState.NEUTRAL)
        val pcSaw = mutableListOf<Pair<Long, Boolean>>() // (time, centred) of reports that REACHED the PC
        var dropNextStop = true
        q.offer(PendingReport(report(GamepadState(leftX = 100)), 0))
        for (t in 0L..400L) {
            if (t == 50L) q.offer(PendingReport(neutral, t)) // thumb lifted
            val st = s.next(q, t)
            if (st is SendStep.Send) {
                val isStop = st.report.bytes.contentEquals(neutral)
                // the stack DROPS the first stop (congested); the confirmations must repair it
                if (isStop && dropNextStop) dropNextStop = false else pcSaw += t to isStop
                s.onSent(st, true, t)
            }
        }
        val firstCentred = pcSaw.first { it.second }.first
        assertTrue("the PC is centred about 30 ms after the lift even though the stop was lost: $pcSaw", firstCentred in 75L..90L)
        assertEquals("stop + two confirmations: $pcSaw", 2, pcSaw.count { it.second })
        assertTrue("nothing more after that", s.next(q, 1000) is SendStep.Idle)
    }

    private fun decodeState(b: ByteArray): GamepadState {
        val d = HidReportManager.decode(b)
        return GamepadState(buttons = d.buttons, leftX = d.x, leftY = d.y, rightX = d.z, rightY = d.rz)
    }
}
