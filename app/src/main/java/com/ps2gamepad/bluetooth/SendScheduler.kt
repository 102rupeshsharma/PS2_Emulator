package com.ps2gamepad.bluetooth

/** What the sender thread should do next. */
sealed interface SendStep {
    /** Nothing to send: sleep until a report is queued. */
    data object Idle : SendStep
    /** Something is due later: sleep at most [ms] (a newly queued report wakes the sender earlier). */
    data class Wait(val ms: Long) : SendStep
    /** Send [report] now. [confirm] = a repeat of the last state (see [SendScheduler]). */
    data class Send(val report: PendingReport, val confirm: Boolean = false) : SendStep
}

/**
 * Decides when each gamepad report goes to the Bluetooth stack (3.2.0). Pure logic with an explicit
 * clock, so it is tested against a simulated Bluetooth link.
 *
 * Why: `BluetoothHidDevice.sendReport()` never blocks and has no flow control. When reports are
 * handed over faster than the Bluetooth link to the PC carries them, they pile up in the stack's
 * queue, and everything after them, including "stick released", arrives late. The player keeps
 * walking for a second after the thumb lifts, and buttons feel slow. So:
 *
 *  1. PRIORITY reports go out at once (every [fastMs]): any button / D-pad change, and a stick
 *     returning to centre (a "stop").
 *  2. Plain stick MOVEMENT is limited to one report every [axisMs] (about 80 Hz; games read the
 *     pad once per frame, 60 Hz, so nothing is lost). Movement that is superseded by a newer report
 *     is dropped instead of being sent late: every report holds the complete state.
 *  3. After every priority report the same state is CONFIRMED twice ([CONFIRM_DELAYS_MS] later), so a
 *     report the stack dropped while busy cannot leave a button or a stick stuck on the PC.
 */
class SendScheduler(
    private val fastMs: Long = FAST_MS,
    /** Interval for movement-only reports (read every time: battery saver may change it). */
    private val axisMs: () -> Long = { AXIS_MS },
) {
    /** Last report the stack accepted. */
    var lastSent: ByteArray? = null
        private set
    private var lastSendAt = Long.MIN_VALUE / 2
    private var confirmIndex = CONFIRM_DELAYS_MS.size // none pending
    private var confirmBase = 0L

    fun next(queue: ReportQueue, now: Long): SendStep {
        while (true) {
            val head = queue.peek()
            if (head == null) {
                if (confirmIndex >= CONFIRM_DELAYS_MS.size || lastSent == null) return SendStep.Idle
                val due = confirmBase + CONFIRM_DELAYS_MS[confirmIndex]
                return if (now >= due) SendStep.Send(PendingReport(lastSent!!, now), confirm = true) else SendStep.Wait(due - now)
            }
            val priority = isPriority(head.bytes)
            // movement superseded by a newer complete report: drop it rather than send it late
            if (!priority && queue.size > 1) { queue.poll(); continue }
            val interval = if (priority) fastMs else axisMs()
            val wait = interval - (now - lastSendAt)
            return if (wait > 0) SendStep.Wait(wait) else SendStep.Send(queue.poll()!!)
        }
    }

    /** The stack accepted (or refused) [step]'s report at [now]. */
    fun onSent(step: SendStep.Send, ok: Boolean, now: Long) {
        if (!ok) return
        val priority = isPriority(step.report.bytes)
        lastSendAt = now
        lastSent = step.report.bytes
        when {
            step.confirm -> confirmIndex++
            priority -> { confirmIndex = 0; confirmBase = now }
            else -> confirmIndex = CONFIRM_DELAYS_MS.size // fresh movement supersedes pending confirms
        }
    }

    /** Host gone: nothing is pending any more. */
    fun reset() {
        lastSent = null
        confirmIndex = CONFIRM_DELAYS_MS.size
    }

    /** A button/D-pad change, or the sticks coming back to centre. */
    fun isPriority(r: ByteArray): Boolean {
        val prev = lastSent ?: return true
        if (!HidReportManager.sameDigitalState(prev, r)) return true
        return axesCentred(r) && !axesCentred(prev)
    }

    private fun axesCentred(r: ByteArray) = r[2].toInt() == 0 && r[3].toInt() == 0 && r[4].toInt() == 0 && r[5].toInt() == 0

    companion object {
        const val FAST_MS = 4L
        /** Movement-only reports: about 80 Hz. */
        const val AXIS_MS = 12L
        /** Movement-only reports in battery saver: about 60 Hz. */
        const val AXIS_MS_SAVER = 16L
        /** After a button change / stop, the same state is sent again this long after it (ms). */
        val CONFIRM_DELAYS_MS = longArrayOf(30L, 100L)
    }
}
