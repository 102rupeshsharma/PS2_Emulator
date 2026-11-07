package com.ps2gamepad.ui.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.pc.PcDefaults
import com.ps2gamepad.pc.PcMouseSettings
import com.ps2gamepad.pc.PcProfile
import com.ps2gamepad.pc.PcProfileManager
import com.ps2gamepad.ui.ActionButton
import com.ps2gamepad.ui.Card
import com.ps2gamepad.ui.LabeledSlider
import com.ps2gamepad.ui.LabeledSwitch
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Palette
import com.ps2gamepad.ui.Screen
import com.ps2gamepad.ui.ScreenFrame
import com.ps2gamepad.ui.mult
import com.ps2gamepad.ui.pct

private sealed interface Confirm {
    data class Delete(val p: PcProfile) : Confirm
    data class Restore(val p: PcProfile) : Confirm
}

/** PC game profiles (separate from emulator loadouts) + the active profile's mouse/gyro settings. */
@Composable
fun PcProfilesScreen(vm: MainViewModel) {
    val store by vm.profiles.collectAsStateWithLifecycle()
    val pc = store.pc
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val newId = { java.util.UUID.randomUUID().toString().take(8) }

    ScreenFrame("PC profiles & settings", onBack = { vm.navigate(Screen.PC_CONTROLLER) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${pc.profiles.size} / ${PcDefaults.MAX_PROFILES} profiles · each keeps its buttons, keys, layout, gyro and mouse settings",
                    color = Palette.TextDim, fontSize = 11.sp)
                for (p in pc.profiles) {
                    val active = p.id == pc.activeId
                    Column(
                        Modifier.fillMaxWidth().background(if (active) Palette.SurfaceHigh else Palette.Surface, RoundedCornerShape(10.dp)).padding(8.dp).testTag("pcprofile_${p.id}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (renaming == p.id) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(renameText, { renameText = it.take(32) }, singleLine = true, modifier = Modifier.weight(1f).testTag("pc_renameField"))
                                ActionButton("Save") { vm.updatePc { PcProfileManager.rename(it, p.id, renameText) }; renaming = null }
                            }
                        } else {
                            Text(p.name + if (active) "  (in use)" else "", color = Palette.Text, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            Text("${p.controls.size} controls · ${PcProfileManager.customCount(p)} custom · gyro ${if (p.mouse.gyroEnabled) "on" else "off"}",
                                color = Palette.TextDim, fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!active) ActionButton("Use") { vm.updatePc { PcProfileManager.select(it, p.id) } }
                            ActionButton("Rename", outlined = true) { renaming = p.id; renameText = p.name }
                            ActionButton("Duplicate", outlined = true, enabled = PcProfileManager.canCreate(pc)) { vm.updatePc { PcProfileManager.duplicate(it, p.id, newId) } }
                            if (p.builtIn) ActionButton("Restore", outlined = true) { confirm = Confirm.Restore(p) }
                            else ActionButton("Delete", outlined = true) { confirm = Confirm.Delete(p) }
                        }
                    }
                }
                if (PcProfileManager.canCreate(pc)) {
                    Card {
                        Text("New profile (copy of the one in use)", color = Palette.TextDim, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(newName, { newName = it.take(32) }, singleLine = true, label = { Text("e.g. Elden Ring") }, modifier = Modifier.weight(1f).testTag("pc_newName"))
                            ActionButton("Create", enabled = newName.isNotBlank()) { vm.updatePc { PcProfileManager.create(it, newName, it.activeId, newId) }; newName = "" }
                        }
                    }
                }
            }
            Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val m = pc.active.mouse
                fun set(f: (PcMouseSettings) -> PcMouseSettings) = vm.updatePc { s -> PcProfileManager.updateActive(s) { it.copy(mouse = f(it.mouse)) } }
                Card {
                    Text("PC input (Bluetooth keyboard + mouse)", color = Palette.TextDim, fontSize = 12.sp)
                    LabeledSwitch("Enabled", store.pcInputEnabled, "sw_pcInput") { vm.setPcInputEnabled(it) }
                    Text("Changing this re-registers the phone. In Windows: Bluetooth & devices → remove the phone → pair again once.",
                        color = Palette.TextDim, fontSize = 10.sp)
                }
                Card {
                    LabeledSwitch("Black theme (both controllers)", store.darkControls, "sw_pcDark") { vm.setDarkControls(it) }
                    LabeledSwitch("Battery saver (dim 60 Hz screen, 125 Hz Bluetooth)", store.batterySaver, "sw_pcSaver") { vm.setBatterySaver(it) }
                }
                Card {
                    Text("Gyro mouse: ${pc.active.name}", color = Palette.TextDim, fontSize = 12.sp)
                    LabeledSwitch("Gyro moves the mouse", m.gyroEnabled, "sw_pcGyro") { v -> set { it.copy(gyroEnabled = v) } }
                    LabeledSlider("Mouse sensitivity", m.gyroSensitivity, 0.1f..5f, ::mult, "s_pcSens") { v -> set { it.copy(gyroSensitivity = v) } }
                    LabeledSlider("X sensitivity", m.gyroSensitivityX, 0.1f..3f, ::mult, "s_pcSensX") { v -> set { it.copy(gyroSensitivityX = v) } }
                    LabeledSlider("Y sensitivity", m.gyroSensitivityY, 0.1f..3f, ::mult, "s_pcSensY") { v -> set { it.copy(gyroSensitivityY = v) } }
                    LabeledSlider("Dead zone", m.gyroDeadZoneDps, 0f..10f, { "%.1f °/s".format(it) }, "s_pcDead") { v -> set { it.copy(gyroDeadZoneDps = v) } }
                    LabeledSlider("Smoothing", m.gyroSmoothing, 0f..1f, ::pct, "s_pcSmooth") { v -> set { it.copy(gyroSmoothing = v) } }
                    LabeledSwitch("Invert X", m.gyroInvertX, "sw_pcInvX") { v -> set { it.copy(gyroInvertX = v) } }
                    LabeledSwitch("Invert Y", m.gyroInvertY, "sw_pcInvY") { v -> set { it.copy(gyroInvertY = v) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionButton("Calibrate (keep still)", outlined = true) { vm.navigate(Screen.PC_CONTROLLER); vm.pcGyroCalibrate() }
                    }
                    Text("Calibration measures the gyroscope's drift: lay the phone still for 1.5 s right after tapping.", color = Palette.TextDim, fontSize = 10.sp)
                }
                Card {
                    Text("Sticks", color = Palette.TextDim, fontSize = 12.sp)
                    LabeledSlider("Right stick mouse speed", m.stickMouseSpeed, 100f..4000f, { "%.0f px/s".format(it) }, "s_pcStickSpeed") { v -> set { it.copy(stickMouseSpeed = v) } }
                    LabeledSlider("Move keys threshold", m.moveKeyThreshold, 0.15f..0.9f, ::pct, "s_pcMoveThr") { v -> set { it.copy(moveKeyThreshold = v) } }
                }
            }
        }
    }

    when (val c = confirm) {
        is Confirm.Delete -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete \"${c.p.name}\"?") },
            text = { Text("Its buttons, layout and settings will be lost.") },
            confirmButton = { TextButton({ vm.updatePc { PcProfileManager.delete(it, c.p.id) }; confirm = null }, modifier = Modifier.testTag("pc_confirmDelete")) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
        is Confirm.Restore -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Restore \"${c.p.name}\" to its defaults?") },
            text = { Text("Your changes to this profile (buttons, layout, settings) will be replaced.") },
            confirmButton = { TextButton({ vm.updatePc { PcProfileManager.restoreDefaults(it, c.p.id) }; confirm = null }, modifier = Modifier.testTag("pc_confirmRestoreProfile")) { Text("Restore") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}
