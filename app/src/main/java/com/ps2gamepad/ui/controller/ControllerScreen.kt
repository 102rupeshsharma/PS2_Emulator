package com.ps2gamepad.ui.controller

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.controller.ControlRect
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.LayoutEngine
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.model.ControllerBackground
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.ControllerProfile
import com.ps2gamepad.model.ExtraButton
import com.ps2gamepad.model.Stick
import com.ps2gamepad.ui.ConnectionChip
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Palette
import com.ps2gamepad.ui.Screen
import com.ps2gamepad.ui.overallStatus
import kotlinx.coroutines.delay

/** Places a child by its centre and size (dp values). */
private fun Modifier.place(r: ControlRect) = this.offset(r.left.dp, r.top.dp).size(r.w.dp, r.h.dp)

/** Size/position of a user-added button on a [w] x [h] dp screen (same size rules for play and edit). */
internal fun extraRect(e: ExtraButton, w: Float, h: Float, a: com.ps2gamepad.model.Appearance): ControlRect {
    val d = LayoutEngine.unit(w, h) * 0.2f * e.scale * a.buttonScale
    return LayoutEngine.clampInside(ControlRect(e.cx * w, e.cy * h, d * 1.3f, d), w, h)
}

/** Short label for a user-added button (PlayStation symbols for the face buttons). */
internal fun extraLabel(b: ControllerButton) = when (b) {
    ControllerButton.TRIANGLE -> "Y"; ControllerButton.CIRCLE -> "B"
    ControllerButton.CROSS -> "A"; ControllerButton.SQUARE -> "X"
    ControllerButton.SELECT -> "SEL"; ControllerButton.START -> "START"
    else -> b.label
}

/** Face-button colour for an added button that presses a face button. */
internal fun extraAccent(b: ControllerButton) = when (b) {
    ControllerButton.TRIANGLE -> FaceColors.Y; ControllerButton.CIRCLE -> FaceColors.B
    ControllerButton.CROSS -> FaceColors.A; ControllerButton.SQUARE -> FaceColors.X
    else -> null
}

/** Full-screen landscape DualShock 2 style controller, plus the layout editor. */
@Composable
fun ControllerScreen(vm: MainViewModel) {
    val epoch by vm.input.resetEpoch.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val editing by vm.editMode.collectAsStateWithLifecycle()
    val bt by vm.bluetoothState.collectAsStateWithLifecycle()
    val perm by vm.permission.collectAsStateWithLifecycle()
    val hid by vm.hidState.collectAsStateWithLifecycle()
    val status = overallStatus(bt, perm, hid)
    val store by vm.profiles.collectAsStateWithLifecycle()
    val neu = NeuThemes.of(store.darkControls)
    var showOverlay by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Back: press twice within 2 s to leave, so a stray edge swipe cannot end a game session.
    var lastBack by remember { mutableLongStateOf(0L) }
    var backHint by remember { mutableStateOf(false) }
    BackHandler {
        if (editing) { vm.setEditMode(false); return@BackHandler }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) vm.navigate(Screen.HOME) else { lastBack = now; backHint = true; vm.input.releaseAll() }
    }
    LaunchedEffect(backHint) { if (backHint) { delay(2000); backHint = false } }

    CompositionLocalProvider(LocalNeu provides neu) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(neu.bg).windowInsetsPadding(WindowInsets.displayCutout).testTag("controller")
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        if (editing) {
            LayoutEditor(vm, profile, w, h)
            return@BoxWithConstraints
        }
        val rects = remember(w, h, profile.appearance, profile.placements) { LayoutEngine.resolve(w, h, profile.appearance, profile.placements) }
        val gyroActive by vm.gyroSending.collectAsStateWithLifecycle()
        ControllerLayout(vm.input, epoch, profile, rects, interactive = true, gyroActive = gyroActive, onGyroRecenter = vm::gyroRecenter)

        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallChipButton("Home") { vm.navigate(Screen.HOME) }
            ConnectionChip(status, Modifier.testTag("connectionChip"), bg = neu.chip, fg = neu.text)
            SmallChipButton(if (showOverlay) "Hide" else "Live") { showOverlay = !showOverlay }
            SmallChipButton("Edit") { vm.setEditMode(true) }
            SmallChipButton("Settings") { vm.input.releaseAll(); showSettings = true }
            if (gyroActive) SmallChipButton("Recenter") { vm.gyroRecenter() }
        }
        if (!hid.isConnected) {
            Text(
                "NOT CONNECTED - inputs are not reaching the PC",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp).testTag("notConnectedBanner")
                    .background(Palette.Bad.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (backHint) {
            Text(
                "Press back again to leave the controller", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).background(Color(0xDD000000), RoundedCornerShape(8.dp)).padding(10.dp),
            )
        }
        if (showOverlay) LiveOverlay(vm, Modifier.align(Alignment.Center).width(260.dp))
        if (showSettings) ControllerSettingsPanel(vm, profile, store.darkControls, Modifier.align(Alignment.Center)) { showSettings = false }
    }
    }
}

/**
 * Draws the background and every control at [rects]. Used by the live controller (interactive),
 * the layout editor and the customisation preview (not interactive).
 */
@Composable
fun ControllerLayout(
    input: ControllerInputManager,
    epoch: Int,
    profile: ControllerProfile,
    rects: Map<ControlId, ControlRect>,
    interactive: Boolean,
    /** Gyro drives the right stick: show the live gyro indicator instead of the touch stick. */
    gyroActive: Boolean = false,
    onGyroRecenter: () -> Unit = {},
    /** User-added buttons (the layout editor passes its unsaved draft). */
    extras: List<ExtraButton> = profile.extraButtons,
) {
    val a = profile.appearance
    val neu = LocalNeu.current
    val skin = Skins.of(neu)
    // 1.8.0: no controller body art, just the plain soft-UI background
    NeuScreenBackground(neu, Modifier.fillMaxSize())
    val style = ControlStyle(showLabels = a.showLabels, haptics = a.haptics && interactive, faceSpacing = a.buttonSpacing, interactive = interactive)
    CompositionLocalProvider(LocalControlStyle provides style, LocalSkin provides skin) {
        Box(Modifier.fillMaxSize().alpha(a.buttonOpacity)) {
            // place + per-control opacity
            fun Modifier.at(id: ControlId) = this.place(rects.getValue(id)).alpha(a.opacityOf(id))
            fun r(id: ControlId) = rects.getValue(id)
            fun fs(id: ControlId, k: Float) = (r(id).h * k).sp
            // test tags keep the PS2 names; the visible label comes from the style
            for ((id, b, tag) in listOf(
                Triple(ControlId.L2, ControllerButton.L2, "L2"), Triple(ControlId.L1, ControllerButton.L1, "L1"),
                Triple(ControlId.R2, ControllerButton.R2, "R2"), Triple(ControlId.R1, ControllerButton.R1, "R1"),
            )) {
                val label = when (id) { ControlId.L2 -> skin.labelL2; ControlId.L1 -> skin.labelL1; ControlId.R2 -> skin.labelR2; else -> skin.labelR1 }
                DigitalButton(b, input, epoch, label, KeyShape.ROUNDED, fs(id, 0.36f), Modifier.at(id).testTag("btn_$tag"))
            }

            DpadControl(input, epoch, Modifier.at(ControlId.DPAD).testTag("dpad"))
            // 1.9.0: four independent buttons (move, resize and overlap each one on its own)
            ControlId.FACE_BUTTONS.forEachIndexed { i, id ->
                val b = when (id) { ControlId.TRIANGLE -> ControllerButton.TRIANGLE; ControlId.CIRCLE -> ControllerButton.CIRCLE; ControlId.CROSS -> ControllerButton.CROSS; else -> ControllerButton.SQUARE }
                DigitalButton(b, input, epoch, FaceColors.LETTERS[i], KeyShape.CIRCLE, fs(id, 0.42f), Modifier.at(id).testTag("btn_${b.name}"), accent = FaceColors.COLORS[i])
            }
            AnalogStick(Stick.LEFT, input, epoch, profile.left, Modifier.at(ControlId.LEFT_STICK).testTag("stick_left"))
            if (gyroActive) GyroStickIndicator(input, onGyroRecenter, Modifier.at(ControlId.RIGHT_STICK).testTag("gyro_indicator"))
            else AnalogStick(Stick.RIGHT, input, epoch, profile.right, Modifier.at(ControlId.RIGHT_STICK).testTag("stick_right"))
            DigitalButton(ControllerButton.L3, input, epoch, skin.labelL3, KeyShape.CIRCLE, fs(ControlId.L3, 0.3f), Modifier.at(ControlId.L3).testTag("btn_L3"))
            DigitalButton(ControllerButton.R3, input, epoch, skin.labelR3, KeyShape.CIRCLE, fs(ControlId.R3, 0.3f), Modifier.at(ControlId.R3).testTag("btn_R3"))
            DigitalButton(ControllerButton.SELECT, input, epoch, skin.labelSelect, KeyShape.PILL, fs(ControlId.SELECT, 0.34f), Modifier.at(ControlId.SELECT).testTag("btn_SELECT"))
            DigitalButton(ControllerButton.START, input, epoch, skin.labelStart, KeyShape.PILL, fs(ControlId.START, 0.34f), Modifier.at(ControlId.START).testTag("btn_START"))
            if (extras.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxSize()) {
                val cw = maxWidth.value
                val ch = maxHeight.value
                for (e in extras) {
                    val r = extraRect(e, cw, ch, a)
                    DigitalButton(
                        e.button, input, epoch, extraLabel(e.button), KeyShape.ROUNDED, (r.h * 0.34f).sp,
                        Modifier.place(r).testTag("extra_${e.id}"), source = "extra_${e.id}", accent = extraAccent(e.button),
                    )
                }
            }
        }
    }
}

/**
 * Layout editor. Controls are drawn non-interactive (no HID input is possible). Tap selects,
 * drag moves, the slider resizes the selected control. A move or resize that would overlap
 * another control is refused (the control snaps back). Nothing is saved until "Save".
 */
@Composable
private fun LayoutEditor(vm: MainViewModel, profile: ControllerProfile, w: Float, h: Float) {
    val defaults = remember(w, h, profile.appearance) { LayoutEngine.defaults(w, h, profile.appearance) }
    // Read through the State object in gesture callbacks: they can run several times between
    // recompositions, so a captured/updated-on-recompose copy would be stale (drops were lost).
    val draftState = remember(w, h) { mutableStateOf(LayoutEngine.resolve(w, h, profile.appearance, profile.placements)) }
    var draft by draftState
    val extrasState = remember(w, h) { mutableStateOf(profile.extraButtons) }
    var extras by extrasState
    var selected by remember { mutableStateOf<ControlId?>(null) }
    var selectedExtra by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>("Drag a control to move it (overlap is fine). Tap it, then drag the blue corner to resize.") }
    val density = LocalDensity.current.density

    ControllerLayout(vm.input, 0, profile, draft, interactive = false, extras = extras)

    // Invisible touch handles on top of each control: the control itself moves under the finger.
    // Only the selected control gets a thin outline in its own shape (no boxes around everything).
    for (id in ControlId.editable) {
        val rect = draft.getValue(id)
        Box(
            Modifier.place(rect)
                .testTag("edit_${id.name}")
                .pointerInput(id) { detectTapGestures { selected = id; selectedExtra = null } }
                .pointerInput(id, draftState) {
                    var start: ControlRect? = null
                    detectDragGestures(
                        onDragStart = { selected = id; selectedExtra = null; start = draftState.value.getValue(id) },
                        onDrag = { change, amount ->
                            change.consume()
                            val r = draftState.value.getValue(id)
                            draft = draftState.value + (id to r.copy(cx = r.cx + amount.x / density, cy = r.cy + amount.y / density))
                        },
                        onDragEnd = {
                            // overlapping is allowed: only keep the control on screen
                            draft = draftState.value + (id to LayoutEngine.clampInside(draftState.value.getValue(id), w, h))
                            message = "${id.label} moved"
                        },
                        onDragCancel = { start?.let { draft = draftState.value + (id to it) } },
                    )
                }
        )
    }

    // user-added buttons: tap selects, drag moves (they may sit anywhere, even over other controls)
    for (e in extras) {
        val r = extraRect(e, w, h, profile.appearance)
        Box(
            Modifier.place(r)
                .testTag("editx_${e.id}")
                .pointerInput(e.id) { detectTapGestures { selectedExtra = e.id; selected = null } }
                .pointerInput(e.id, extrasState) {
                    detectDragGestures(
                        onDragStart = { selectedExtra = e.id; selected = null },
                        onDrag = { change, amount ->
                            change.consume()
                            extras = extrasState.value.map {
                                if (it.id != e.id) it
                                else it.copy(cx = (it.cx + amount.x / density / w).coerceIn(0f, 1f), cy = (it.cy + amount.y / density / h).coerceIn(0f, 1f))
                            }
                        },
                    )
                }
        )
    }

    // Selected control: dashed frame, blue corner handle (drag = resize), red x (delete an added button).
    val selRect: ControlRect? = selected?.let { draft.getValue(it) }
        ?: extras.firstOrNull { it.id == selectedExtra }?.let { extraRect(it, w, h, profile.appearance) }
    if (selRect != null) {
        val pad = 5f
        val frame = ControlRect(selRect.cx, selRect.cy, selRect.w + 2 * pad, selRect.h + 2 * pad)
        Box(
            Modifier.place(frame).testTag("editFrame").drawBehind {
                drawRoundRect(
                    Palette.Info, cornerRadius = CornerRadius(8.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
        )
        val handle = 24f
        Box(
            Modifier.offset((frame.right - handle / 2).dp, (frame.bottom - handle / 2).dp).size(handle.dp)
                .background(Palette.Info, CircleShape).border(2.dp, Color.White, CircleShape)
                .testTag("resizeHandle")
                .pointerInput(selected, selectedExtra) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val grow = (amount.x + amount.y) / 2f / density
                        val sel = selected
                        val sx = selectedExtra
                        if (sel != null) {
                            val d = defaults.getValue(sel)
                            val cur = draftState.value.getValue(sel)
                            val sc = ((cur.w + grow * 2f) / d.w).coerceIn(0.5f, 2f)
                            draft = draftState.value + (sel to LayoutEngine.clampInside(cur.copy(w = d.w * sc, h = d.h * sc), w, h))
                        } else if (sx != null) {
                            extras = extrasState.value.map {
                                if (it.id != sx) it else {
                                    val cur = extraRect(it, w, h, profile.appearance)
                                    it.copy(scale = (it.scale * (cur.w + grow * 2f) / cur.w).coerceIn(0.5f, 2f))
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text("↘", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        if (selectedExtra != null) {
            Box(
                Modifier.offset((frame.left - handle / 2).dp, (frame.top - handle / 2).dp).size(handle.dp)
                    .background(Palette.Bad, CircleShape).border(2.dp, Color.White, CircleShape)
                    .clickable { extras = extras.filter { it.id != selectedExtra }; selectedExtra = null; message = "Button removed (press Save to keep)" }
                    .testTag("extraDeleteX"),
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.background(Color(0xE6000000), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("EDIT LAYOUT", color = Palette.Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            SmallChipButton("Save") {
                // the legacy face cluster is never saved: the four buttons carry their own placements
                val placements = draft.filter { (id, r) -> id != ControlId.FACE && r != defaults.getValue(id) }
                    .mapValues { (id, r) -> LayoutEngine.toPlacement(r, defaults.getValue(id), w, h) }
                vm.saveLayout(placements, extras)
                vm.log.add("Layout saved (${placements.size} control(s) moved/resized, ${extras.size} added button(s))")
                vm.setEditMode(false)
            }
            SmallChipButton("Cancel") { vm.setEditMode(false) }
            SmallChipButton("Defaults") { draft = defaults; message = "Default positions (press Save to keep)" }
            if (extras.size < ControllerProfile.MAX_EXTRA_BUTTONS) SmallChipButton("+ Button") {
                val e = ExtraButton(java.util.UUID.randomUUID().toString().take(6), ControllerButton.CROSS, 0.5f, 0.55f)
                extras = extras + e
                selectedExtra = e.id; selected = null
                message = "Button added: drag it into place, then choose what it presses"
            }
        }
        val selX = extras.firstOrNull { it.id == selectedExtra }
        if (selX != null) {
            Column(
                Modifier.padding(top = 4.dp).background(Color(0xE6000000), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp).testTag("extraEditor"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                fun change(f: (ExtraButton) -> ExtraButton) { extras = extras.map { if (it.id == selX.id) f(it) else it } }
                Text("Added button presses: ${selX.button.label}", color = Color.White, fontSize = 11.sp)
                for (row in ControllerButton.entries.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                        for (b in row) MapChip(b.label, b == selX.button, "map_${b.name}") { change { it.copy(button = b) } }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size ${(selX.scale * 100).toInt()} %", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                    Slider(value = selX.scale, valueRange = 0.5f..2f, modifier = Modifier.width(200.dp).testTag("extraSize"), onValueChange = { v -> change { it.copy(scale = v) } })
                    MapChip("Delete", false, "extra_delete") { extras = extras.filter { it.id != selX.id }; selectedExtra = null; message = "Button removed (press Save to keep)" }
                }
            }
        }
        val sel = selected
        if (sel != null) {
            val scale = draft.getValue(sel).w / defaults.getValue(sel).w
            Row(
                Modifier.padding(top = 4.dp).background(Color(0xE6000000), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${sel.label} size ${(scale * 100).toInt()} %", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(140.dp))
                Slider(
                    value = scale, valueRange = 0.5f..2f, modifier = Modifier.width(200.dp).testTag("sizeSlider"),
                    onValueChange = { sc ->
                        val d = defaults.getValue(sel)
                        val cur = draft.getValue(sel)
                        draft = draft + (sel to LayoutEngine.clampInside(cur.copy(w = d.w * sc, h = d.h * sc), w, h))
                    },
                )
                Text("Opacity ${(profile.appearance.opacityOf(sel) * 100).toInt()} %", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                Slider(
                    value = profile.appearance.opacityOf(sel), valueRange = com.ps2gamepad.model.Appearance.MIN_CONTROL_OPACITY..1f,
                    modifier = Modifier.width(160.dp).testTag("opacitySlider"),
                    onValueChange = { vm.setControlOpacity(sel, it) },
                )
            }
        }
        message?.let {
            Text(it, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp).background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun MapChip(text: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.background(if (on) Palette.Info else Color(0xFF2A2F3A), RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp).testTag(tag),
    )
}

@Composable
private fun SmallChipButton(text: String, onClick: () -> Unit) {
    val neu = LocalNeu.current
    Text(
        text, color = neu.text, fontSize = 11.sp,
        modifier = Modifier.background(neu.chip, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp).testTag("chip_$text"),
    )
}

/** Compact live readout, so inputs can be checked while using the real controller. */
@Composable
private fun LiveOverlay(vm: MainViewModel, modifier: Modifier) {
    val pad by vm.gamepad.collectAsStateWithLifecycle()
    val pointers by vm.input.activePointers.collectAsStateWithLifecycle()
    val last by vm.input.lastReport.collectAsStateWithLifecycle()
    Column(modifier.background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(6.dp)) {
        val t = 9.sp
        Text(HidReportManager.describe(pad), color = Color(0xFFB8F5C8), fontSize = t, fontFamily = FontFamily.Monospace)
        Text("pointers=$pointers", color = Color(0xFFB8F5C8), fontSize = t, fontFamily = FontFamily.Monospace)
        Text("last: " + (last?.let { "01 " + HidReportManager.toHex(it) } ?: "-"), color = Color(0xFFB8F5C8), fontSize = t, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Right stick when the gyro drives it: shows the value that is in the HID report (read in the
 * draw phase, so only this canvas redraws). Not a touch control: tap = recenter.
 */
@Composable
private fun GyroStickIndicator(input: ControllerInputManager, onRecenter: () -> Unit, modifier: Modifier) {
    val state = input.state.collectAsStateWithLifecycle()
    val neu = LocalNeu.current
    Box(modifier.clickable(onClick = onRecenter), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val travel = s * STICK_TRAVEL
            val g = state.value
            val k = Offset(g.rightX / 127f * travel, g.rightY / 127f * travel)
            drawNeuStick(neu, center, travel, s, k, held = false)
            drawCircle(neu.accent, radius = s * 0.025f, center = center + k) // red dot = gyro
        }
        Text("GYRO · tap to recenter", color = neu.label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Quick settings inside the controller: opacity and size of all controls, and the gyro switch.
 * With the gyro ON the right joystick becomes the gyro automatically. Changes apply live and are
 * saved to the loadout in use. The panel takes all touches, so nothing behind it is pressed.
 */
@Composable
private fun ControllerSettingsPanel(vm: MainViewModel, profile: ControllerProfile, dark: Boolean, modifier: Modifier, onClose: () -> Unit) {
    val a = profile.appearance
    val gyroSending by vm.gyroSending.collectAsStateWithLifecycle()
    val neu = LocalNeu.current
    Column(
        modifier.width(380.dp).padding(vertical = 40.dp)
            .background(neu.panel, RoundedCornerShape(18.dp))
            .border(1.dp, neu.labelDim.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .verticalScroll(androidx.compose.foundation.rememberScrollState()) // short screens: nothing is cut off
            .padding(16.dp)
            .testTag("controllerSettings"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Controller settings", color = neu.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SmallChipButton("Close") { onClose() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gyro aiming", color = neu.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("ON: the right joystick becomes the gyro (tilt the phone to look around)", color = neu.textDim, fontSize = 11.sp)
            }
            androidx.compose.material3.Switch(
                checked = profile.gyro.enabled,
                onCheckedChange = { on -> vm.updateGyro { it.copy(enabled = on) } },
                modifier = Modifier.testTag("set_gyro"),
            )
        }
        if (profile.gyro.enabled) {
            Text(
                if (gyroSending) "Hold the phone the way you play, then tap Recenter." else "Starting gyro…",
                color = neu.textDim, fontSize = 11.sp,
            )
            SmallChipButton("Recenter gyro") { vm.gyroRecenter() }
        }
        val saver = vm.profiles.collectAsStateWithLifecycle().value.batterySaver
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Battery saver", color = neu.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("ON: 60 Hz screen and 125 Hz Bluetooth while playing: less battery and heat. " +
                    "Brightness stays yours (phone settings).", color = neu.textDim, fontSize = 11.sp)
            }
            androidx.compose.material3.Switch(checked = saver, onCheckedChange = { vm.setBatterySaver(it) }, modifier = Modifier.testTag("set_saver"))
        }
        SettingSlider("Opacity (all controls)", a.buttonOpacity, 0.2f..1f, "set_opacity") { v -> vm.updateAppearance { it.copy(buttonOpacity = v) } }
        SettingSlider("Button size (all buttons)", a.buttonScale, 0.6f..1.6f, "set_buttonSize") { v -> vm.updateAppearance { it.copy(buttonScale = v) } }
        SettingSlider("Joystick size", a.stickScale, 0.6f..1.6f, "set_stickSize") { v -> vm.updateAppearance { it.copy(stickScale = v) } }
        SmallChipButton("Reset size & opacity") {
            vm.updateAppearance { it.copy(buttonOpacity = 1f, buttonScale = 1f, stickScale = 1f) }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String, onChange: (Float) -> Unit) {
    Text("$label: ${(value * 100).toInt()} %", color = LocalNeu.current.text, fontSize = 13.sp)
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, modifier = Modifier.testTag(tag))
}
