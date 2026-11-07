package com.ps2gamepad.bluetooth

/** A report waiting to be sent, plus the uptime (ms) of the input event that produced it. */
class PendingReport(val bytes: ByteArray, val eventUptimeMs: Long, val reportId: Int = HidDescriptor.REPORT_ID)

/**
 * Pending-report queue for the Bluetooth sender thread.
 *
 * Touch screens can produce several hundred move events per second. Consecutive reports that
 * differ only in analog axes are coalesced into the newest one, which keeps latency bounded. A
 * report that changes any button or the hat is never merged away, so a quick press-and-release
 * is never lost.
 *
 * Not thread-safe; [HidDeviceManager] guards it.
 */
class ReportQueue(private val capacity: Int = 64) {
    private val items = ArrayDeque<PendingReport>()

    val size: Int get() = items.size
    fun isEmpty(): Boolean = items.isEmpty()

    fun offer(report: PendingReport) {
        val last = items.lastOrNull()
        if (last != null && HidReportManager.sameDigitalState(last.bytes, report.bytes)) {
            // Only the axes changed since the last queued report: replace it with the newest values.
            items[items.lastIndex] = report
            return
        }
        if (items.size >= capacity) {
            // Should not happen at realistic rates. Drop the oldest; later reports carry the
            // complete current button/key state, so held inputs stay correct.
            items.removeFirst()
        }
        items.addLast(report)
    }

    fun poll(): PendingReport? = items.removeFirstOrNull()

    fun clear() = items.clear()
}
