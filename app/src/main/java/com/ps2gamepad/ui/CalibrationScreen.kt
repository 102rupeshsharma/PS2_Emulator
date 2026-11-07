package com.ps2gamepad.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.Stick
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.ui.controller.AnalogStick
import kotlinx.coroutines.delay

/**
 * Live analog test + centre calibration. Each side has a real stick (it sends HID reports, so the
 * result is also visible in joy.cpl) and a visualizer: grey dot = raw finger position, coloured dot
 * = the value sent; rings = dead zone and max range.
 */
@Composable
fun CalibrationScreen(vm: MainViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val pad by vm.gamepad.collectAsStateWithLifecycle()
    val epoch by vm.input.resetEpoch.collectAsStateWithLifecycle()
    ScreenFrame("Analog calibration: ${profile.name}", onBack = { vm.navigate(Screen.HOME) }) {
        @Composable
        fun one(stick: Stick, m: Modifier) {
            val s = profile.stick(stick)
            val hidX = if (stick == Stick.LEFT) pad.leftX else pad.rightX
            val hidY = if (stick == Stick.LEFT) pad.leftY else pad.rightY
            StickCalibration(vm, stick, s, epoch, hidX, hidY, m)
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= WIDE_LAYOUT) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (stick in Stick.entries) one(stick, Modifier.weight(1f))
                }
            } else {
                // portrait phone: the two sticks one under the other
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (stick in Stick.entries) one(stick, Modifier.fillMaxWidth().height(330.dp))
                }
            }
        }
    }
}

@Composable
private fun StickCalibration(vm: MainViewModel, stick: Stick, s: StickSettings, epoch: Int, hidX: Int, hidY: Int, modifier: Modifier) {
    var raw by remember { mutableStateOf<Offset?>(null) }
    var out by remember { mutableStateOf(StickVector.ZERO) }
    var capturing by remember { mutableStateOf(false) }
    val samples = remember { mutableStateListOf<Offset>() }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        samples.clear()
        delay(3000)
        capturing = false
        if (samples.size < 10) {
            note = "Not enough samples: keep your thumb resting on the pad for the whole 3 s."
        } else {
            val ax = samples.map { it.x }.average().toFloat().coerceIn(-StickSettings.MAX_CENTER_OFFSET, StickSettings.MAX_CENTER_OFFSET)
            val ay = samples.map { it.y }.average().toFloat().coerceIn(-StickSettings.MAX_CENTER_OFFSET, StickSettings.MAX_CENTER_OFFSET)
            vm.calibrateCenter(stick, ax, ay)
            note = "Centre set to (%.2f, %.2f) of travel from ${samples.size} samples.".format(ax, ay)
        }
    }

    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (stick == Stick.LEFT) "Left" else "Right") + " stick · " + (if (s.floating) "floating" else "fixed") +
                " · HID X=$hidX Y=$hidY",
            color = Palette.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("hid_$stick"),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalogStick(
                stick, vm.input, epoch, s,
                Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true).background(Palette.Surface, RoundedCornerShape(12.dp)).testTag("calStick_$stick"),
            ) { r, o ->
                raw = r; out = o
                if (capturing && r != null) samples.add(r)
            }
            Visualizer(raw, out, s, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton(if (capturing) "Hold still..." else "Calibrate centre (3 s)", enabled = !capturing && !s.floating) { note = null; capturing = true }
            ActionButton("Reset centre", outlined = true) { vm.calibrateCenter(stick, 0f, 0f); note = "Centre reset" }
        }
        Text(
            note ?: if (s.floating) "Calibration only applies in fixed mode (floating uses the touch-down point)."
            else "Rest your thumb where it naturally sits on the stick, then calibrate.",
            color = Palette.TextDim, fontSize = 11.sp,
        )
    }
}

@Composable
private fun Visualizer(raw: Offset?, out: StickVector, s: StickSettings, modifier: Modifier) {
    Canvas(modifier.background(Color.Black, RoundedCornerShape(12.dp))) {
        val r = size.minDimension * 0.42f
        val c = center
        drawLine(Color(0xFF333333), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
        drawLine(Color(0xFF333333), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        drawCircle(Color(0xFF555555), radius = r, center = c, style = Stroke(2f))
        drawCircle(Palette.Warn.copy(alpha = 0.6f), radius = r * s.deadZone, center = c, style = Stroke(2f))
        drawCircle(Palette.Info.copy(alpha = 0.5f), radius = r * s.maxRange, center = c, style = Stroke(2f))
        // calibrated centre (fixed mode)
        if (!s.floating) drawCircle(Color.White.copy(alpha = 0.5f), radius = 5f, center = c + Offset(s.centerOffsetX * r, s.centerOffsetY * r))
        raw?.let { drawCircle(Color.Gray, radius = 9f, center = c + Offset(it.x.coerceIn(-1.2f, 1.2f) * r, it.y.coerceIn(-1.2f, 1.2f) * r)) }
        drawCircle(Palette.Good, radius = 11f, center = c + Offset(out.x * r, out.y * r))
    }
}
