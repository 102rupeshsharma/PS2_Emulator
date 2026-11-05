package com.ps2gamepad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.GyroSettings
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Gyro right stick: live test + settings. The circle shows the right-stick value that is actually
 * in the HID report (from the input manager) while the gyro is sending, otherwise the computed value.
 */
@Composable
fun GyroScreen(vm: MainViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val reading by vm.gyroReading.collectAsStateWithLifecycle()
    val sending by vm.gyroSending.collectAsStateWithLifecycle()
    val pad by vm.gamepad.collectAsStateWithLifecycle()
    val g = profile.gyro
    val status = vm.gyroStatus

    // The values shown: what goes to the PC (HID axes) when sending, else the computed stick.
    val hx = if (sending) pad.rightX else com.ps2gamepad.controller.StickMath.toAxis(reading.stick.x)
    val hy = if (sending) pad.rightY else com.ps2gamepad.controller.StickMath.toAxis(reading.stick.y)
    val nx = hx / GamepadState.AXIS_MAX.toFloat()
    val ny = hy / GamepadState.AXIS_MAX.toFloat()
    val magnitude = hypot(nx, ny).coerceAtMost(1f)

    ScreenFrame("Gyro right stick", onBack = { vm.navigate(Screen.HOME) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---------------- live view
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDot(if (status.usable) Severity.GOOD else Severity.BAD)
                    Text(
                        when {
                            !status.hasGyroscope -> "No gyroscope on this phone"
                            !status.hasAccelerometer -> "No accelerometer on this phone"
                            reading.calibrating -> "Calibrating… hold still"
                            reading.sampleRateHz > 0 -> "Sensors OK · ${reading.sampleRateHz} Hz"
                            else -> "Starting sensors…"
                        },
                        color = Palette.Text, fontSize = 12.sp, modifier = Modifier.testTag("gyroStatus"),
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StickCircle(nx, ny, sending, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true).testTag("gyroCircle"))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val mono = FontFamily.Monospace
                        Text("X  %+.2f  (HID %d)".format(nx, hx), color = Palette.Text, fontFamily = mono, fontSize = 13.sp, modifier = Modifier.testTag("gyroX"))
                        Text("Y  %+.2f  (HID %d)".format(ny, hy), color = Palette.Text, fontFamily = mono, fontSize = 13.sp, modifier = Modifier.testTag("gyroY"))
                        Text("Magnitude  %.2f".format(magnitude), color = Palette.Text, fontFamily = mono, fontSize = 13.sp)
                        Text("Direction  " + directionLabel(nx, ny), color = Palette.Text, fontFamily = mono, fontSize = 13.sp)
                        Text("Tilt  L/R %+.1f°  F/B %+.1f°".format(reading.tilt.xDeg, reading.tilt.yDeg), color = Palette.TextDim, fontFamily = mono, fontSize = 12.sp)
                        Text(
                            if (sending) "Sending to the PC as the RIGHT stick" else if (g.enabled) "Not sending (open the controller)" else "Gyro is OFF: values are not sent",
                            color = if (sending) Palette.Good else Palette.Warn, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("gyroSendState"),
                        )
                    }
                }
            }
            // ---------------- settings
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Card {
                    LabeledSwitch("Gyro controls the right stick", g.enabled, "sw_gyro") { v -> vm.updateGyro { it.copy(enabled = v) } }
                    Text("When ON, the on-screen right stick is replaced by phone tilt.", color = Palette.TextDim, fontSize = 10.sp)
                }
                Card {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Recenter") { vm.gyroRecenter() }
                        ActionButton("Calibrate (1.5 s)", outlined = true) { vm.gyroCalibrate() }
                    }
                    Text("Hold the phone the way you play, then tap. That hold becomes the centre.", color = Palette.TextDim, fontSize = 10.sp)
                }
                Card {
                    LabeledSlider("Sensitivity", g.sensitivity, 0.25f..3f, ::mult, "s_gyroSens") { v -> vm.updateGyro { it.copy(sensitivity = v) } }
                    LabeledSlider("Max tilt angle", g.maxTiltDeg, GyroSettings.MIN_TILT..GyroSettings.MAX_TILT, { "%.0f°".format(it) }, "s_gyroMax") { v ->
                        vm.updateGyro { it.copy(maxTiltDeg = v) }
                    }
                    LabeledSlider("Dead zone", g.deadZoneDeg, 0f..GyroSettings.MAX_DEAD_ZONE, { "%.1f°".format(it) }, "s_gyroDead") { v ->
                        vm.updateGyro { it.copy(deadZoneDeg = v) }
                    }
                    LabeledSlider("Smoothing", g.smoothing, 0f..1f, ::pct, "s_gyroSmooth") { v -> vm.updateGyro { it.copy(smoothing = v) } }
                    LabeledSwitch("Invert X (left/right)", g.invertX, "sw_gyroInvX") { v -> vm.updateGyro { it.copy(invertX = v) } }
                    LabeledSwitch("Invert Y (up/down)", g.invertY, "sw_gyroInvY") { v -> vm.updateGyro { it.copy(invertY = v) } }
                    ActionButton("Defaults", outlined = true) { vm.updateGyro { GyroSettings(enabled = it.enabled) } }
                }
            }
        }
    }
}

/** e.g. "Up-Right 37°" (0° = right, counter-clockwise), or "Centre". */
fun directionLabel(x: Float, y: Float): String {
    if (hypot(x, y) < 0.01f) return "Centre"
    val deg = ((Math.toDegrees(atan2(-y, x).toDouble()) + 360.0) % 360.0)
    val names = listOf("Right", "Up-Right", "Up", "Up-Left", "Left", "Down-Left", "Down", "Down-Right")
    val sector = (((deg + 22.5) % 360) / 45).toInt()
    return "%s %.0f°".format(names[sector], deg)
}

@Composable
private fun StickCircle(x: Float, y: Float, sending: Boolean, modifier: Modifier) {
    Canvas(modifier.background(Color.Black, RoundedCornerShape(16.dp))) {
        val r = size.minDimension * 0.42f
        val c = center
        drawCircle(Color(0xFF1C2030), radius = r, center = c)
        drawCircle(Color(0xFF3A4060), radius = r, center = c, style = Stroke(3f))
        drawCircle(Color(0xFF2A3048), radius = r * 0.5f, center = c, style = Stroke(1.5f))
        drawLine(Color(0xFF2A3048), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
        drawLine(Color(0xFF2A3048), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        val k = c + Offset(x * r, y * r)
        val col = if (sending) Palette.Good else Palette.Info
        drawLine(col.copy(alpha = 0.5f), c, k, strokeWidth = 3f)
        drawCircle(col.copy(alpha = 0.25f), radius = r * 0.2f, center = k)
        drawCircle(col, radius = r * 0.12f, center = k)
    }
}
