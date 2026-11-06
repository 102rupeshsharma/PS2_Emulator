package com.ps2gamepad.ui.pc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.bluetooth.KeyCodes
import com.ps2gamepad.bluetooth.MouseButton
import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.pc.PcAction
import com.ps2gamepad.pc.PcActionType
import com.ps2gamepad.pc.PcControl
import com.ps2gamepad.pc.PcControlKind
import com.ps2gamepad.pc.PcDefaults
import com.ps2gamepad.pc.PcProfileManager
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Palette
import com.ps2gamepad.ui.Screen
import com.ps2gamepad.ui.Severity
import com.ps2gamepad.ui.overallStatus
import com.ps2gamepad.ui.controller.trackPointers
import com.ps2gamepad.ui.controller.LocalNeu
import com.ps2gamepad.ui.controller.NeuThemes
import com.ps2gamepad.ui.controller.drawNeuStick
import com.ps2gamepad.ui.controller.neuBackground
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.hypot
import kotlin.math.min

// Soft-UI look shared with the emulator controller (1.8.0): colours come from LocalNeu.
private val PcAccent = Color(0xFF06B6D4)
private val PcPanelEdge = Color(0xFF34405C)
private val PcKeyChip = Color(0xFF1B2232)

/** Layout geometry shared by play and edit mode: control size in dp for a screen of [w] x [h]. */
internal object PcGeometry {
    fun unit(w: Float, h: Float) = min(h, w / 2.4f)
    fun sizeDp(c: PcControl, u: Float) = when (c.kind) {
        PcControlKind.BUTTON -> u * 0.17f * c.size
        PcControlKind.STICK_KEYS, PcControlKind.STICK_MOUSE -> u * 0.44f * c.size
    }
}

/** PC Gaming controller: play mode (sends keyboard/mouse) and edit mode (move/resize/edit/add). */
@Composable
fun PcControllerScreen(vm: MainViewModel) {
    val store by vm.profiles.collectAsStateWithLifecycle()
    val editing by vm.pcEditMode.collectAsStateWithLifecycle()
    val epoch by vm.pcResetEpoch.collectAsStateWithLifecycle()
    val hid by vm.hidState.collectAsStateWithLifecycle()
    val bt by vm.bluetoothState.collectAsStateWithLifecycle()
    val perm by vm.permission.collectAsStateWithLifecycle()
    val gyroPaused by vm.pcGyroPaused.collectAsStateWithLifecycle()
    val gyroRunning by vm.pcGyroRunning.collectAsStateWithLifecycle()
    val profile = store.pc.active
    val neu = NeuThemes.of(store.darkControls)
    var selected by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    BackHandler { if (editing) vm.setPcEditMode(false) else vm.navigate(Screen.MODE_SELECT) }

    CompositionLocalProvider(LocalNeu provides neu) {
    BoxWithConstraints(Modifier.fillMaxSize().background(neu.bg).windowInsetsPadding(WindowInsets.displayCutout).testTag("pcController")) {
        val w = maxWidth.value
        val h = maxHeight.value
        val u = PcGeometry.unit(w, h)
        val density = LocalDensity.current.density

        for (c in profile.controls) {
            val d = PcGeometry.sizeDp(c, u)
            val boxW = if (c.kind == PcControlKind.BUTTON && c.label.length > 4) d * 1.25f else d
            val m = Modifier.offset((c.cx * w - boxW / 2).dp, (c.cy * h - d / 2).dp).size(boxW.dp, d.dp).alpha(c.opacity)
            if (editing) {
                EditableControl(c, selected == c.id, m, density, w, h,
                    onSelect = { selected = c.id },
                    onMoved = { nx, ny -> vm.updatePc { s -> PcProfileManager.updateControl(s, c.id) { it.copy(cx = nx, cy = ny) } } })
            } else when (c.kind) {
                PcControlKind.BUTTON -> PcButton(vm, c, epoch, m)
                PcControlKind.STICK_KEYS -> PcStick(c, epoch, m, mouse = false, vm)
                PcControlKind.STICK_MOUSE -> PcStick(c, epoch, m, mouse = true, vm)
            }
        }

        // ---------------- top bar
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Chip("+ Button", "pc_add", enabled = PcProfileManager.customCount(profile) < PcDefaults.MAX_CUSTOM_BUTTONS) {
                    vm.updatePc { PcProfileManager.addCustomButton(it, "Button", PcAction.key("E")) { java.util.UUID.randomUUID().toString().take(6) } }
                    selected = vm.pcStore.active.controls.lastOrNull()?.id
                }
                Chip("Restore default layout", "pc_restore") { confirmRestore = true }
                Chip("Done", "pc_done") { selected = null; vm.setPcEditMode(false) }
            } else {
                // compact: must fit between LT and RT on a phone
                Chip("◀", "pc_modes") { vm.navigate(Screen.MODE_SELECT) }
                Chip(if (profile.name.length > 14) profile.name.take(13) + "…" else profile.name, "pc_profile") { vm.navigate(Screen.PC_PROFILES) }
                val st = overallStatus(bt, perm, hid)
                StatusChip(if (hid.isConnected) "Online" else "Offline", st.severity)
                Chip(
                    when { !profile.mouse.gyroEnabled -> "Gyro off"; gyroPaused -> "Gyro ‖"; gyroRunning -> "Gyro ON"; else -> "Gyro" },
                    "pc_gyro", highlight = gyroRunning,
                ) { vm.togglePcGyro() }
                Chip("⟳", "pc_recenter") { vm.pcGyroRecenter() }
                Chip("Edit", "pc_edit") { vm.setPcEditMode(true) }
            }
        }
        if (!store.pcInputEnabled && !editing) {
            Column(
                Modifier.align(Alignment.Center).background(Color(0xEE111827), RoundedCornerShape(16.dp)).border(1.dp, Palette.Warn, RoundedCornerShape(16.dp)).padding(16.dp).width(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("PC input is off", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("PC Mode sends keyboard and mouse. That needs a one-time switch of the phone's Bluetooth type: after enabling, remove the phone in Windows Bluetooth settings and pair it again. Emulator Mode keeps working afterwards.",
                    color = Palette.TextDim, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().background(PcAccent, RoundedCornerShape(50)).clickable { vm.setPcInputEnabled(true) }.padding(10.dp).testTag("pc_enableInput"), contentAlignment = Alignment.Center) {
                    Text("Enable PC input", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (!hid.isConnected && !editing) {
            // bottom centre, between the sticks: never covers a control
            Text("Not connected: nothing reaches the PC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).background(Palette.Bad.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp).testTag("pc_notConnected"))
        }

        if (editing) {
            val sel = profile.controls.firstOrNull { it.id == selected }
            if (sel != null) {
                ControlEditor(vm, sel, Modifier.align(if (sel.cx > 0.5f) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(330.dp).padding(top = 40.dp)) { selected = null }
            } else {
                Text("EDIT: drag to move · tap a control to change its name, key, size, opacity", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(6.dp))
            }
        }
    }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore the default layout?") },
            text = { Text("All controls of \"${profile.name}\" go back to their default keys and positions. Custom buttons are removed.") },
            confirmButton = { TextButton(onClick = { vm.updatePc { PcProfileManager.restoreDefaults(it, it.activeId) }; confirmRestore = false; selected = null }, modifier = Modifier.testTag("pc_confirmRestore")) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Chip(text: String, tag: String, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit) {
    val neu = LocalNeu.current
    Text(
        text, color = when { highlight -> Color.White; enabled -> neu.text; else -> neu.textDim }, fontSize = 11.sp, maxLines = 1,
        modifier = Modifier.background(if (highlight) PcAccent.copy(alpha = 0.85f) else neu.chip, RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp).testTag(tag),
    )
}

@Composable
private fun StatusChip(text: String, severity: Severity) {
    val neu = LocalNeu.current
    Row(
        Modifier.background(neu.chip, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp).testTag("pc_status"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(severity.color, CircleShape))
        Text(text, color = neu.text, fontSize = 11.sp, maxLines = 1)
    }
}

/** A PC button: held while a finger is on it; sends its action's down/up. */
@Composable
private fun PcButton(vm: MainViewModel, c: PcControl, epoch: Int, modifier: Modifier) {
    var pressed by remember(epoch) { mutableStateOf(false) }
    val neu = LocalNeu.current
    Box(
        modifier.trackPointers(epoch) { pointers, t ->
            val now = pointers.isNotEmpty()
            if (now != pressed) {
                pressed = now
                if (now) vm.pcPress(c, t) else vm.pcRelease(c, t)
            }
        }.neuBackground(neu, pressed)
            .testTag("pc_${c.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(c.label, color = neu.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(c.action.label, color = neu.labelDim, fontSize = 9.sp, maxLines = 1)
        }
    }
}

/**
 * PC stick (fixed centre). Movement keys: the 8-way direction (equal 45° sectors) past the
 * threshold presses the matching keys (e.g. W+D). Mouse: deflection -> cursor speed.
 */
@Composable
private fun PcStick(c: PcControl, epoch: Int, modifier: Modifier, mouse: Boolean, vm: MainViewModel) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    var knob by remember(epoch) { mutableStateOf(Offset.Zero) }
    var keys by remember(epoch) { mutableStateOf(emptySet<Int>()) }
    val threshold = vm.pcStore.active.mouse.moveKeyThreshold
    val neu = LocalNeu.current
    var held by remember(epoch) { mutableStateOf(false) }
    Box(modifier.testTag("pc_${c.id}"), contentAlignment = Alignment.BottomCenter) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { area = it }.trackPointers(epoch) { pointers, t ->
                val travel = min(area.width, area.height) * 0.30f
                val p = pointers.firstOrNull()
                held = p != null
                if (p == null) {
                    knob = Offset.Zero
                    if (mouse) vm.pcMouseStick(0f, 0f) else if (keys.isNotEmpty()) { keys = emptySet(); vm.pcMoveKeys(c, keys, t) }
                    return@trackPointers
                }
                val dx = p.position.x - area.width / 2f
                val dy = p.position.y - area.height / 2f
                val dist = hypot(dx, dy)
                knob = if (dist > travel) Offset(dx / dist * travel, dy / dist * travel) else Offset(dx, dy)
                val v = StickMath.process(dx, dy, travel, StickSettings(deadZone = 0.08f))
                if (mouse) {
                    vm.pcMouseStick(v.x, v.y)
                } else {
                    val d = StickMath.dpadFromOffset(v.x, v.y, 1f, innerDeadZone = threshold, threshold = 0.38f)
                    val next = buildSet { if (d.up) add(c.up); if (d.down) add(c.down); if (d.left) add(c.left); if (d.right) add(c.right) }
                    if (next != keys) { keys = next; vm.pcMoveKeys(c, next, t) }
                }
            }
        ) {
            val s = size.minDimension
            val travel = s * 0.30f
            drawNeuStick(neu, center, travel, s, knob, held)
        }
        Text(
            if (mouse) "${c.label} · mouse" else "${c.label} · ${KeyCodes.name(c.up)}${KeyCodes.name(c.left)}${KeyCodes.name(c.down)}${KeyCodes.name(c.right)}",
            color = neu.labelDim, fontSize = 9.sp,
        )
    }
}

/** Edit mode: drawn non-interactive (no input is sent); drag moves, tap selects. */
@Composable
private fun EditableControl(
    c: PcControl, selected: Boolean, modifier: Modifier, density: Float, w: Float, h: Float,
    onSelect: () -> Unit, onMoved: (Float, Float) -> Unit,
) {
    var drag by remember(c.id, c.cx, c.cy) { mutableStateOf(Offset.Zero) }
    val neu = LocalNeu.current
    Box(
        modifier.offset { androidx.compose.ui.unit.IntOffset(drag.x.toInt(), drag.y.toInt()) } // moves in layout phase, no recomposition
            .neuBackground(neu, pressed = false, cornerFraction = if (c.kind == PcControlKind.BUTTON) 0.3f else 0.5f)
            .border(if (selected) 3.dp else 1.dp, if (selected) PcAccent else Color.White.copy(alpha = 0.5f), if (c.kind == PcControlKind.BUTTON) RoundedCornerShape(30) else CircleShape)
            .pointerInput(c.id) { detectTapGestures { onSelect() } }
            .pointerInput(c.id, c.cx, c.cy) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { ch, amount -> ch.consume(); drag += amount },
                    onDragEnd = {
                        val nx = (c.cx + drag.x / density / w).coerceIn(0.03f, 0.97f)
                        val ny = (c.cy + drag.y / density / h).coerceIn(0.05f, 0.95f)
                        drag = Offset.Zero
                        onMoved(nx, ny)
                    },
                    onDragCancel = { drag = Offset.Zero },
                )
            }
            .testTag("pcedit_${c.id}"),
        contentAlignment = Alignment.Center,
    ) { Text(c.label, color = neu.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Side panel to edit the selected control. Every change is saved immediately to the active profile. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ControlEditor(vm: MainViewModel, c: PcControl, modifier: Modifier, onClose: () -> Unit) {
    fun update(f: (PcControl) -> PcControl) = vm.updatePc { s -> PcProfileManager.updateControl(s, c.id, f) }
    var name by remember(c.id) { mutableStateOf(c.label) }
    var stickDir by remember(c.id) { androidx.compose.runtime.mutableIntStateOf(0) } // 0 up, 1 down, 2 left, 3 right
    Column(
        modifier.background(Color(0xF0111827), RoundedCornerShape(16.dp)).border(1.dp, PcPanelEdge, RoundedCornerShape(16.dp))
            .padding(12.dp).verticalScroll(rememberScrollState()).testTag("pc_editor"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (c.custom) "Custom button" else "Control", color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Chip("Close", "pc_editorClose", onClick = onClose)
        }
        OutlinedTextField(name, { v -> name = v.take(PcControl.MAX_LABEL); update { it.copy(label = name) } }, singleLine = true,
            label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("pc_name"))

        if (c.kind == PcControlKind.BUTTON) {
            Text("Action", color = Palette.TextDim, fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((type, label) in listOf(PcActionType.KEY to "Key", PcActionType.MOUSE to "Mouse", PcActionType.WHEEL_UP to "Wheel ▲",
                    PcActionType.WHEEL_DOWN to "Wheel ▼", PcActionType.GYRO_TOGGLE to "Gyro on/off", PcActionType.GYRO_RECENTER to "Recenter")) {
                    FilterChip(c.action.type == type, { update { it.copy(action = it.action.copy(type = type)) } }, { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("pc_type_$type"))
                }
            }
            when (c.action.type) {
                PcActionType.KEY -> {
                    Text("Hold with (combination)", color = Palette.TextDim, fontSize = 11.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((bit, n) in PcAction.MOD_NAMES) {
                            FilterChip(c.action.modifiers and bit != 0, { update { it.copy(action = it.action.copy(modifiers = it.action.modifiers xor bit)) } },
                                { Text(n, fontSize = 11.sp) }, modifier = Modifier.testTag("pc_mod_$n"))
                        }
                    }
                    Text("Key: ${KeyCodes.name(c.action.key)}", color = Color.White, fontSize = 13.sp)
                    KeyGrid(c.action.key) { k -> update { it.copy(action = it.action.copy(key = k)) } }
                }
                PcActionType.MOUSE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (b in MouseButton.entries) FilterChip(c.action.mouse == b, { update { it.copy(action = it.action.copy(mouse = b)) } },
                        { Text(b.label, fontSize = 11.sp) }, modifier = Modifier.testTag("pc_mouse_${b.name}"))
                }
                else -> Unit
            }
        } else if (c.kind == PcControlKind.STICK_KEYS) {
            Text("Movement keys", color = Palette.TextDim, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Up" to c.up, "Down" to c.down, "Left" to c.left, "Right" to c.right).forEachIndexed { i, (n, k) ->
                    FilterChip(stickDir == i, { stickDir = i }, { Text("$n: ${KeyCodes.name(k)}", fontSize = 10.sp) })
                }
            }
            val current = when (stickDir) { 0 -> c.up; 1 -> c.down; 2 -> c.left; else -> c.right }
            KeyGrid(current) { k -> update { when (stickDir) { 0 -> it.copy(up = k); 1 -> it.copy(down = k); 2 -> it.copy(left = k); else -> it.copy(right = k) } } }
        } else {
            Text("Moves the mouse. Speed: PC profile settings.", color = Palette.TextDim, fontSize = 11.sp)
        }

        Text("Size ${(c.size * 100).toInt()} %", color = Color.White, fontSize = 12.sp)
        Slider(c.size, { v -> update { it.copy(size = v) } }, valueRange = 0.5f..2.5f, modifier = Modifier.testTag("pc_size"))
        Text("Opacity ${(c.opacity * 100).toInt()} %", color = Color.White, fontSize = 12.sp)
        Slider(c.opacity, { v -> update { it.copy(opacity = v) } }, valueRange = 0.15f..1f, modifier = Modifier.testTag("pc_opacity"))
        if (c.custom) {
            Box(Modifier.fillMaxWidth().background(Palette.Bad.copy(alpha = 0.8f), RoundedCornerShape(50)).clickable {
                vm.updatePc { PcProfileManager.deleteControl(it, c.id) }; onClose()
            }.padding(8.dp).testTag("pc_delete"), contentAlignment = Alignment.Center) { Text("Delete button", color = Color.White) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KeyGrid(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (k in KeyCodes.ALL) {
            Text(
                k.name, color = if (k.usage == selected) Color.Black else Color.White, fontSize = 11.sp,
                modifier = Modifier.background(if (k.usage == selected) PcAccent else PcKeyChip, RoundedCornerShape(6.dp))
                    .clickable { onPick(k.usage) }.padding(horizontal = 7.dp, vertical = 5.dp).testTag("pc_key_${k.name}"),
            )
        }
    }
}
