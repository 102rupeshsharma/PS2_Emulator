package com.ps2gamepad.bluetooth

/** A report waiting to be sent, plus the uptime (ms) of the input event that produced it. */
class PendingReport(val bytes: ByteArray, val eventUptimeMs: Long, val reportId: Int = HidDescriptor.REPORT_ID)

/**
 * Pending-report queue for the Bluetooth sender thread.
 *
 * - Gamepad (ID 1): touch screens can produce several hundred move events per second.
 *   Consecutive reports that differ only in analog axes are coalesced into the newest one, which
 *   keeps latency bounded. A report that changes any button or the hat is never merged away, so a
 *   quick press-and-release is never lost.
 * - Mouse (ID 3, PC Mode): relative movement must never be dropped, so consecutive movement-only
 *   reports with the same buttons are SUMMED.
 * - Keyboard (ID 2, PC Mode): every report is a key transition and is never merged.
 *
 * Not thread-safe; [HidDeviceManager] guards it.
 */
class ReportQueue(private val capacity: Int = 64) {
    private val items = ArrayDeque<PendingReport>()

    val size: Int get() = items.size
    fun isEmpty(): Boolean = items.isEmpty()

    fun offer(report: PendingReport) {
        val last = items.lastOrNull()
        if (last != null && last.reportId == report.reportId) {
            when (report.reportId) {
                HidDescriptor.REPORT_ID -> if (HidReportManager.sameDigitalState(last.bytes, report.bytes)) {
                    // Only the axes changed since the last queued report: replace it with the newest values.
                    items[items.lastIndex] = report
                    return
                }
                HidDescriptor.MOUSE_REPORT_ID -> PcReports.mergeMouse(last.bytes, report.bytes)?.let {
                    items[items.lastIndex] = PendingReport(it, report.eventUptimeMs, report.reportId)
                    return
                }
            }
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
