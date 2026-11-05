package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.PendingReport
import com.ps2gamepad.bluetooth.ReportQueue
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.Stick
import com.ps2gamepad.model.StickSettings
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Repeatable micro-benchmark of the app's input pipeline on the DEVELOPMENT PC's JVM.
 * It measures software cost only (touch math -> state -> HID report -> send queue) and is NOT a
 * phone, Bluetooth or end-to-end latency figure. Results: app/build/perf/benchmark.txt.
 * The loose upper bounds only catch gross regressions.
 */
class PerformanceBenchmarkTest {
    private fun <T> timeNs(iterations: Int, warmup: Int = 20_000, block: (Int) -> T): Double {
        repeat(warmup) { block(it) }
        val t0 = System.nanoTime()
        repeat(iterations) { block(it) }
        return (System.nanoTime() - t0).toDouble() / iterations
    }

    @Test fun inputPipelineCost() {
        val sent = IntArray(1)
        val m = ControllerInputManager({ _, _ -> sent[0]++; true })
        val settings = StickSettings(deadZone = 0.1f, curve = 1.5f)
        val n = 200_000
        val stickNs = timeNs(n) { i ->
            val a = i * 0.01
            m.setStick(Stick.LEFT, StickMath.process((Math.cos(a) * 80).toFloat(), (Math.sin(a) * 80).toFloat(), 100f, settings))
        }
        val buttonNs = timeNs(n) { i -> m.setButton(ControllerButton.CROSS, i % 2 == 0) }
        val encodeNs = timeNs(n) { i -> HidReportManager.encode(GamepadState(buttons = i and 0xFFF, leftX = i % 127)) }
        val q = ReportQueue()
        val queueNs = timeNs(n) { i -> q.offer(PendingReport(HidReportManager.NEUTRAL_REPORT, i.toLong())); if (q.size > 32) q.clear() }

        val report = """
            Input pipeline micro-benchmark (desktop JVM, ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} cores)
            NOT phone / Bluetooth / end-to-end latency.
            stick move -> process + state + encode + send : %.0f ns/op
            button press/release -> state + encode + send : %.0f ns/op
            encode only                                   : %.0f ns/op
            send-queue offer (coalescing)                 : %.0f ns/op
            reports produced                              : ${sent[0]}
        """.trimIndent().format(stickNs, buttonNs, encodeNs, queueNs)
        File("build/perf").apply { mkdirs() }.resolve("benchmark.txt").writeText(report + "\n")
        println(report)
        assertTrue("stick pipeline regressed: $stickNs ns", stickNs < 50_000)
        assertTrue("button pipeline regressed: $buttonNs ns", buttonNs < 50_000)
    }
}
