package com.ps2gamepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.controller.LayoutEngine
import com.ps2gamepad.model.ControllerBackground
import com.ps2gamepad.model.Stick
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.model.Appearance
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.ui.controller.ControllerPreview

/**
 * Appearance + analog settings of the active profile. Every change is applied to the profile at
 * once (the preview and the controller use the same state) and saved automatically.
 * Nothing here can change which HID button a control sends.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(vm: MainViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val a = profile.appearance
    var stickTab by remember { mutableStateOf(Stick.LEFT) }
    var opacityTarget by remember { mutableStateOf(ControlId.FACE) }

    ScreenFrame("Customize: ${profile.name}", onBack = { vm.navigate(Screen.HOME) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preview (same layout as the controller, not interactive)", color = Palette.TextDim, fontSize = 11.sp)
                ControllerPreview(
                    vm.input, profile,
                    Modifier.fillMaxWidth().border(1.dp, Palette.SurfaceHigh, RoundedCornerShape(8.dp)).testTag("preview"),
                )
                val overlaps = remember(a, profile.placements) { LayoutEngine.overlaps(LayoutEngine.resolve(900f, 900f / 2.2f, a, profile.placements)) }
                if (overlaps.isNotEmpty()) {
                    Text(
                        "Warning: ${overlaps.joinToString { "${it.first.label}/${it.second.label}" }} overlap at this size. Reduce sizes or move them in Edit layout.",
                        color = Palette.Warn, fontSize = 11.sp, modifier = Modifier.testTag("overlapWarning"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Edit layout") { vm.navigate(Screen.CONTROLLER); vm.setEditMode(true) }
                    ActionButton("Reset layout", outlined = true, onClick = vm::resetLayout)
                    ActionButton("Reset look", outlined = true, onClick = vm::resetAppearance)
                }
            }

            Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Card {
                    Text("Appearance", color = Palette.TextDim, fontSize = 12.sp)
                    LabeledSlider("Button size", a.buttonScale, 0.6f..1.6f, ::pct, "s_buttonScale") { v -> vm.updateAppearance { it.copy(buttonScale = v) } }
                    LabeledSlider("Stick size", a.stickScale, 0.6f..1.6f, ::pct, "s_stickScale") { v -> vm.updateAppearance { it.copy(stickScale = v) } }
                    LabeledSlider("Opacity, all controls", a.buttonOpacity, 0.2f..1f, ::pct, "s_buttonOpacity") { v -> vm.updateAppearance { it.copy(buttonOpacity = v) } }
                    LabeledSlider("Controller opacity", a.controllerOpacity, 0f..1f, ::pct, "s_controllerOpacity") { v -> vm.updateAppearance { it.copy(controllerOpacity = v) } }
                    LabeledSlider("Face button spacing", a.buttonSpacing, 0.8f..1.3f, ::pct, "s_spacing") { v -> vm.updateAppearance { it.copy(buttonSpacing = v) } }
                    Text("Background", color = Palette.Text, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ControllerBackground.entries.forEach { bg ->
                            FilterChip(selected = a.background == bg, onClick = { vm.updateAppearance { it.copy(background = bg) } },
                                label = { Text(bg.label, fontSize = 11.sp) }, modifier = Modifier.testTag("bg_${bg.name}"))
                        }
                    }
                    LabeledSwitch("Show button labels", a.showLabels, "sw_labels") { v -> vm.updateAppearance { it.copy(showLabels = v) } }
                    LabeledSwitch("Haptic feedback on press", a.haptics, "sw_haptics") { v -> vm.updateAppearance { it.copy(haptics = v) } }
                }
                Card {
                    Text("Opacity of each control (on top of \"all controls\")", color = Palette.TextDim, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ControlId.entries.forEach { id ->
                            val pctText = (a.opacityOf(id) * 100).toInt()
                            FilterChip(selected = opacityTarget == id, onClick = { opacityTarget = id },
                                label = { Text(if (pctText < 100) "${id.label} $pctText%" else id.label, fontSize = 11.sp) },
                                modifier = Modifier.testTag("op_${id.name}"))
                        }
                    }
                    LabeledSlider("${opacityTarget.label} opacity", a.opacityOf(opacityTarget), Appearance.MIN_CONTROL_OPACITY..1f, ::pct, "s_controlOpacity") { v ->
                        vm.setControlOpacity(opacityTarget, v)
                    }
                    ActionButton("All controls to 100 %", outlined = true) { vm.updateAppearance { it.copy(controlOpacity = emptyMap()) } }
                }
                Card {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Analog", color = Palette.TextDim, fontSize = 12.sp)
                        Stick.entries.forEach { s ->
                            FilterChip(selected = stickTab == s, onClick = { stickTab = s },
                                label = { Text(if (s == Stick.LEFT) "Left stick" else "Right stick", fontSize = 11.sp) }, modifier = Modifier.testTag("tab_$s"))
                        }
                    }
                    StickSettingsEditor(profile.stick(stickTab)) { f -> vm.updateStick(stickTab, f) }
                    ActionButton("Calibrate / live test", outlined = true) { vm.navigate(Screen.CALIBRATION) }
                }
            }
        }
    }
}

@Composable
fun StickSettingsEditor(s: StickSettings, update: ((StickSettings) -> StickSettings) -> Unit) {
    LabeledSlider("Sensitivity", s.sensitivity, 0.5f..2.5f, ::mult, "s_sens") { v -> update { it.copy(sensitivity = v) } }
    LabeledSlider("Dead zone", s.deadZone, 0f..0.4f, ::pct, "s_dead") { v -> update { it.copy(deadZone = v) } }
    LabeledSlider("Max range", s.maxRange, 0.5f..1f, ::pct, "s_max") { v -> update { it.copy(maxRange = v) } }
    LabeledSlider("Response curve", s.curve, StickSettings.MIN_CURVE..3f, { "%.2f".format(it) + if (it > 1.05f) " (fine)" else if (it < 0.95f) " (quick)" else " (linear)" }, "s_curve") { v ->
        update { it.copy(curve = v) }
    }
    LabeledSwitch("Invert Y axis", s.invertY, "sw_invert") { v -> update { it.copy(invertY = v) } }
    LabeledSwitch("Floating stick (centre = where thumb lands)", s.floating, "sw_floating") { v -> update { it.copy(floating = v) } }
}
