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
import com.ps2gamepad.model.Stick
import com.ps2gamepad.ui.ConnectionChip
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Palette
import com.ps2gamepad.ui.Screen
import com.ps2gamepad.ui.overallStatus
import kotlinx.coroutines.delay

/** Places a child by its centre and size (dp values). */
private fun Modifier.place(r: ControlRect) = this.offset(r.left.dp, r.top.dp).size(r.w.dp, r.h.dp)

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
    var showOverlay by remember { mutableStateOf(false) }

    // Back: press twice within 2 s to leave, so a stray edge swipe cannot end a game session.
    var lastBack by remember { mutableLongStateOf(0L) }
    var backHint by remember { mutableStateOf(false) }
    BackHandler {
        if (editing) { vm.setEditMode(false); return@BackHandler }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) vm.navigate(Screen.HOME) else { lastBack = now; backHint = true; vm.input.releaseAll() }
    }
    LaunchedEffect(backHint) { if (backHint) { delay(2000); backHint = false } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Skins.of(profile.appearance.skin).screen).windowInsetsPadding(WindowInsets.displayCutout).testTag("controller")
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
            ConnectionChip(status, Modifier.testTag("connectionChip"))
            SmallChipButton(if (showOverlay) "Hide" else "Live") { showOverlay = !showOverlay }
            SmallChipButton("Edit") { vm.setEditMode(true) }
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
) {
    val a = profile.appearance
    val skin = Skins.of(a.skin)
    ControllerBackgroundArt(a.background, skin, Modifier.fillMaxSize().alpha(a.controllerOpacity))
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
            FaceButtons(input, epoch, Modifier.at(ControlId.FACE).testTag("face"))
            AnalogStick(Stick.LEFT, input, epoch, profile.left, Modifier.at(ControlId.LEFT_STICK).testTag("stick_left"))
            if (gyroActive) GyroStickIndicator(input, onGyroRecenter, Modifier.at(ControlId.RIGHT_STICK).testTag("gyro_indicator"))
            else AnalogStick(Stick.RIGHT, input, epoch, profile.right, Modifier.at(ControlId.RIGHT_STICK).testTag("stick_right"))
            DigitalButton(ControllerButton.L3, input, epoch, skin.labelL3, KeyShape.CIRCLE, fs(ControlId.L3, 0.3f), Modifier.at(ControlId.L3).testTag("btn_L3"))
            DigitalButton(ControllerButton.R3, input, epoch, skin.labelR3, KeyShape.CIRCLE, fs(ControlId.R3, 0.3f), Modifier.at(ControlId.R3).testTag("btn_R3"))
            DigitalButton(ControllerButton.SELECT, input, epoch, skin.labelSelect, KeyShape.PILL, fs(ControlId.SELECT, 0.34f), Modifier.at(ControlId.SELECT).testTag("btn_SELECT"))
            DigitalButton(ControllerButton.START, input, epoch, skin.labelStart, KeyShape.PILL, fs(ControlId.START, 0.34f), Modifier.at(ControlId.START).testTag("btn_START"))
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
    var selected by remember { mutableStateOf<ControlId?>(null) }
    var message by remember { mutableStateOf<String?>("Drag a control to move it. Tap to select and resize.") }
    val density = LocalDensity.current.density

    ControllerLayout(vm.input, 0, profile, draft, interactive = false)

    for (id in ControlId.entries) {
        val rect = draft.getValue(id)
        Box(
            Modifier.place(rect)
                .border(if (id == selected) 3.dp else 1.dp, if (id == selected) Palette.Info else Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .testTag("edit_${id.name}")
                .pointerInput(id) { detectTapGestures { selected = id } }
                .pointerInput(id, draftState) {
                    var start: ControlRect? = null
                    detectDragGestures(
                        onDragStart = { selected = id; start = draftState.value.getValue(id) },
                        onDrag = { change, amount ->
                            change.consume()
                            val r = draftState.value.getValue(id)
                            draft = draftState.value + (id to r.copy(cx = r.cx + amount.x / density, cy = r.cy + amount.y / density))
                        },
                        onDragEnd = {
                            val moved = LayoutEngine.clampInside(draftState.value.getValue(id), w, h)
                            val hits = LayoutEngine.collisions(draftState.value, id, moved)
                            if (hits.isEmpty()) {
                                draft = draftState.value + (id to moved); message = "${id.label} moved"
                            } else {
                                draft = draftState.value + (id to (start ?: moved))
                                message = "${id.label} would overlap ${hits.joinToString { it.label }} - moved back"
                            }
                        },
                        onDragCancel = { start?.let { draft = draftState.value + (id to it) } },
                    )
                }
        )
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
                val placements = draft.filter { (id, r) -> r != defaults.getValue(id) }
                    .mapValues { (id, r) -> LayoutEngine.toPlacement(r, defaults.getValue(id), w, h) }
                vm.savePlacements(placements)
                vm.log.add("Layout saved (${placements.size} control(s) moved/resized)")
                vm.setEditMode(false)
            }
            SmallChipButton("Cancel") { vm.setEditMode(false) }
            SmallChipButton("Defaults") { draft = defaults; message = "Default positions (press Save to keep)" }
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
                        val resized = LayoutEngine.clampInside(cur.copy(w = d.w * sc, h = d.h * sc), w, h)
                        val hits = LayoutEngine.collisions(draft, sel, resized)
                        if (hits.isEmpty()) draft = draft + (sel to resized)
                        else message = "Larger would overlap ${hits.joinToString { it.label }}"
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
private fun SmallChipButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Palette.Text, fontSize = 11.sp,
        modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(50)).clickable(onClick = onClick)
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

/** Background art in the style's colours. Drawn in its own layer; it never recomposes on input. */
@Composable
fun ControllerBackgroundArt(bg: ControllerBackground, skin: SkinTheme, modifier: Modifier) {
    when (bg) {
        ControllerBackground.DS2_BODY -> PadBody(skin, modifier.background(skin.screen))
        ControllerBackground.DARK -> Box(modifier.background(Color(0xFF17191D)))
        ControllerBackground.BLACK -> Box(modifier.background(Color.Black))
        ControllerBackground.BLUE -> Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF14213D), Color(0xFF0A1022)))))
    }
}

@Composable
private fun PadBody(skin: SkinTheme, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val edge = skin.bodyEdge
        val body = Brush.verticalGradient(listOf(skin.bodyTop, skin.bodyBottom))
        for (left in listOf(true, false)) {
            val cx = if (left) w * 0.19f else w * 0.81f
            val gw = w * 0.36f
            val gh = h * 0.82f
            drawOval(edge, topLeft = Offset(cx - gw / 2 - 3f, h * 0.24f - 3f), size = Size(gw + 6f, gh + 6f))
            drawOval(body, topLeft = Offset(cx - gw / 2, h * 0.24f), size = Size(gw, gh))
        }
        drawRoundRect(edge, topLeft = Offset(w * 0.015f - 3f, h * 0.09f - 3f), size = Size(w * 0.97f + 6f, h * 0.62f + 6f), cornerRadius = CornerRadius(h * 0.24f))
        drawRoundRect(body, topLeft = Offset(w * 0.015f, h * 0.09f), size = Size(w * 0.97f, h * 0.62f), cornerRadius = CornerRadius(h * 0.24f))
        for (left in listOf(true, false)) {
            val cx = if (left) w * 0.19f else w * 0.81f
            val gw = w * 0.36f
            drawOval(skin.bodyBottom, topLeft = Offset(cx - gw / 2 + 3f, h * 0.45f), size = Size(gw - 6f, h * 0.61f - 3f))
        }
        // centre panel (touchpad area on the PS5 style), with an accent light bar
        drawRoundRect(skin.centerPanel, topLeft = Offset(w * 0.37f, h * 0.15f), size = Size(w * 0.26f, h * 0.33f), cornerRadius = CornerRadius(h * 0.06f))
        drawRoundRect(skin.accent.copy(alpha = 0.85f), topLeft = Offset(w * 0.44f, h * 0.135f), size = Size(w * 0.12f, h * 0.012f), cornerRadius = CornerRadius(h * 0.01f))
    }
}

/**
 * Right stick when the gyro drives it: shows the value that is in the HID report (read in the
 * draw phase, so only this canvas redraws). Not a touch control: tap = recenter.
 */
@Composable
private fun GyroStickIndicator(input: ControllerInputManager, onRecenter: () -> Unit, modifier: Modifier) {
    val state = input.state.collectAsStateWithLifecycle()
    val skin = LocalSkin.current
    Box(modifier.clickable(onClick = onRecenter), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val travel = s * STICK_TRAVEL
            val c = center
            drawCircle(skin.keyEdge, radius = travel + s * 0.06f, center = c)
            drawCircle(skin.stickBase, radius = travel + s * 0.045f, center = c)
            drawCircle(skin.accent.copy(alpha = 0.8f), radius = travel + s * 0.05f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.012f))
            val g = state.value
            val k = c + Offset(g.rightX / 127f * travel, g.rightY / 127f * travel)
            val kr = s * 0.15f
            drawCircle(Brush.radialGradient(listOf(skin.stickKnobLight, skin.stickKnobDark), center = k, radius = kr * 1.4f), radius = kr, center = k)
            drawCircle(skin.accent, radius = kr, center = k, style = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.01f))
        }
        Text("GYRO · tap to recenter", color = skin.label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
