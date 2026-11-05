package com.ps2gamepad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.model.ControllerButton

/** Phase 2 diagnostic screen: full controller state, link status, measured sender stats, stick tuning. */
@Composable
fun DiagnosticScreen(vm: MainViewModel) {
    val pad by vm.gamepad.collectAsStateWithLifecycle()
    val pointers by vm.input.activePointers.collectAsStateWithLifecycle()
    val last by vm.input.lastReport.collectAsStateWithLifecycle()
    val hid by vm.hidState.collectAsStateWithLifecycle()
    val bt by vm.bluetoothState.collectAsStateWithLifecycle()
    val perm by vm.permission.collectAsStateWithLifecycle()
    val stats by vm.senderStats.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val perf = remember { PerfSampler() }
    var sample by remember { mutableStateOf(perf.sample()) }
    var battery by remember { mutableStateOf(vm.batteryPercent()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            sample = perf.sample()
            battery = vm.batteryPercent()
        }
    }

    BackHandler { vm.navigate(Screen.DASHBOARD) }

    Row(
        Modifier.fillMaxSize().background(Palette.Background).windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Controller Diagnostics", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                ActionButton("Controller", onClick = { vm.navigate(Screen.CONTROLLER) })
                ActionButton("Home", outlined = true, onClick = { vm.navigate(Screen.DASHBOARD) })
            }
            Card {
                val st = overallStatus(bt, perm, hid)
                StatusRow("HID connection", st.text, st.severity)
                StatusRow("Active pointers", pointers.toString(), Severity.IDLE)
                StatusRow("D-pad", pad.dpad.hat.label + " (hat ${pad.dpad.hat.value})", if (pad.dpad.isNeutral) Severity.IDLE else Severity.GOOD)
                StatusRow("Left stick X/Y", "${pad.leftX} / ${pad.leftY}", if (pad.leftX == 0 && pad.leftY == 0) Severity.IDLE else Severity.GOOD)
                StatusRow("Right stick X/Y", "${pad.rightX} / ${pad.rightY}", if (pad.rightX == 0 && pad.rightY == 0) Severity.IDLE else Severity.GOOD)
            }
            Card {
                Text("Buttons", color = Palette.TextDim, fontSize = 12.sp)
                ControllerButton.entries.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { b -> ButtonLamp("${b.label} (B${b.hidButton})", pad.isPressed(b)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ButtonLamp("Up", pad.dpad.up); ButtonLamp("Down", pad.dpad.down)
                    ButtonLamp("Left", pad.dpad.left); ButtonLamp("Right", pad.dpad.right)
                }
            }
            Card {
                Text("Last report sent", color = Palette.TextDim, fontSize = 12.sp)
                Text(last?.let { "01 " + HidReportManager.toHex(it) } ?: "none (no host connected)", color = Palette.Text, fontFamily = FontFamily.Monospace)
                Text(HidReportManager.describe(pad), color = Palette.TextDim, fontSize = 11.sp)
            }
            ActionButton("Reset All Inputs", onClick = vm::releaseAllInputs)
        }

        Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card {
                Text("Measured on the phone (sendReport calls)", color = Palette.TextDim, fontSize = 12.sp)
                StatusRow("Reports sent", stats.reportsSent.toString(), Severity.IDLE)
                StatusRow("Send failures", stats.sendFailures.toString(), if (stats.sendFailures > 0) Severity.WARN else Severity.IDLE)
                StatusRow("Rate (last second)", "${stats.reportsPerSecond} /s", Severity.IDLE)
                StatusRow("Touch -> sendReport, last", if (stats.lastLatencyMs >= 0) "${stats.lastLatencyMs} ms" else "-", Severity.IDLE)
                StatusRow("Touch -> sendReport, avg", if (stats.avgLatencyMs >= 0) "%.1f ms".format(stats.avgLatencyMs) else "-", Severity.IDLE)
                Text("Does not include radio time or Windows processing.", color = Palette.TextDim, fontSize = 10.sp)
            }
            Card {
                Text("Measured on the phone (software)", color = Palette.TextDim, fontSize = 12.sp)
                val t = vm.input.timing
                StatusRow("Report generation avg / max", "%.1f / %.1f us (%d reports)".format(t.avgMicros, t.maxMicros, t.count), Severity.IDLE)
                StatusRow("App CPU (last 2 s)", sample.cpuPercent?.let { "%.1f %% of one core".format(it) } ?: "-", Severity.IDLE)
                StatusRow("Heap used", "%.1f MB".format(sample.heapMb), Severity.IDLE)
                session?.let { ses ->
                    StatusRow("Controller session", formatDuration(vm.uptimeNow() - ses.startUptimeMs), Severity.IDLE)
                    StatusRow("Battery during session", if (ses.startBattery != null && battery != null) "${ses.startBattery} % -> $battery %" else "unknown", Severity.IDLE)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("Reset counters", outlined = true) { vm.input.timing.reset(); vm.resetSession() }
                }
                Text("Report generation = encode + queue for sending. Excludes the radio, Windows and PCSX2.", color = Palette.TextDim, fontSize = 10.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ButtonLamp(label: String, on: Boolean) {
    Box(
        Modifier.background(if (on) Palette.Good else Palette.SurfaceHigh, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (on) Palette.Background else Palette.Text, fontSize = 10.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
    Spacer(Modifier.width(0.dp))
}

/** App CPU % (process CPU time / wall time between samples) and Java heap use. Real measurements. */
class PerfSampler {
    data class Sample(val cpuPercent: Double?, val heapMb: Double)
    private var lastCpu = android.os.Process.getElapsedCpuTime()
    private var lastWall = android.os.SystemClock.elapsedRealtime()

    fun sample(): Sample {
        val cpu = android.os.Process.getElapsedCpuTime()
        val wall = android.os.SystemClock.elapsedRealtime()
        val dWall = wall - lastWall
        val pctCpu = if (dWall > 0 && lastWall > 0) (cpu - lastCpu) * 100.0 / dWall else null
        lastCpu = cpu; lastWall = wall
        val rt = Runtime.getRuntime()
        return Sample(pctCpu, (rt.totalMemory() - rt.freeMemory()) / 1_048_576.0)
    }
}
