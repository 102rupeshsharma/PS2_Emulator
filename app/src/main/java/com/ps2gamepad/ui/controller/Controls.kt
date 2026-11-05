package com.ps2gamepad.ui.controller

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.Dpad
import com.ps2gamepad.model.Hat
import com.ps2gamepad.model.Stick
import com.ps2gamepad.model.StickSettings
import kotlin.math.hypot
import kotlin.math.min

/** Look-and-feel shared by all controls (from the active profile's appearance). */
data class ControlStyle(
    val showLabels: Boolean = true,
    val haptics: Boolean = true,
    val faceSpacing: Float = 1f,
    /** False in the layout editor and in previews: the control draws but never touches HID state. */
    val interactive: Boolean = true,
)

val LocalControlStyle = compositionLocalOf { ControlStyle() }

/** Haptic tick on press only (never on release: release must never wait for anything). */
@Composable
private fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    val style = LocalControlStyle.current
    return remember(view, style.haptics) {
        if (style.haptics) { { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } } else { {} }
    }
}

/** Pointer tracking only when the control is interactive. */
@Composable
private fun Modifier.inputIfInteractive(resetKey: Int, onChange: (List<TrackedPointer>, Long) -> Unit): Modifier =
    if (LocalControlStyle.current.interactive) this.systemGestureExclusion().trackPointers(resetKey, onChange) else this

enum class KeyShape { ROUNDED, PILL, CIRCLE }

/** Any single digital button: held while at least one of its own fingers is down. */
@Composable
fun DigitalButton(
    button: ControllerButton,
    input: ControllerInputManager,
    resetKey: Int,
    label: String,
    shape: KeyShape,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember(resetKey) { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val style = LocalControlStyle.current
    val skin = LocalSkin.current
    Box(
        modifier.inputIfInteractive(resetKey) { pointers, t ->
            val now = pointers.isNotEmpty()
            input.reportPointers(button.name, pointers.size)
            if (now != pressed) {
                pressed = now
                input.setButton(button, now, t)
                if (now) haptic()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fill = if (pressed) skin.keyPressed else skin.key
            val glow = if (skin.glow) (if (pressed) skin.labelPressed else skin.keyEdge) else null
            when (shape) {
                KeyShape.CIRCLE -> {
                    drawCircle(skin.keyEdge, radius = size.minDimension / 2)
                    drawCircle(fill, radius = size.minDimension / 2 * 0.9f)
                    glow?.let { drawCircle(it, radius = size.minDimension / 2 * 0.92f, style = Stroke(size.minDimension * 0.05f)) }
                }
                KeyShape.PILL, KeyShape.ROUNDED -> {
                    val r = if (shape == KeyShape.PILL) size.height / 2 else size.height * 0.28f
                    drawRoundRect(skin.keyEdge, cornerRadius = CornerRadius(r))
                    val inset = size.height * 0.07f
                    drawRoundRect(fill, topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset), cornerRadius = CornerRadius(r - inset))
                    glow?.let {
                        drawRoundRect(it, topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
                            cornerRadius = CornerRadius(r - inset), style = Stroke(size.height * 0.06f))
                    }
                }
            }
        }
        if (style.showLabels) {
            Text(label, color = if (pressed) skin.labelPressed else skin.label, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * D-pad as one control: the direction comes from the finger position relative to the centre,
 * so sliding between arrows and diagonals works. Several fingers are combined.
 */
@Composable
fun DpadControl(input: ControllerInputManager, resetKey: Int, modifier: Modifier = Modifier) {
    var dpad by remember(resetKey) { mutableStateOf(Dpad.NEUTRAL) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val haptic = rememberHaptic()
    val labels = LocalControlStyle.current.showLabels
    val skin = LocalSkin.current
    Canvas(
        modifier
            .onSizeChanged { area = it }
            .inputIfInteractive(resetKey) { pointers, t ->
                val c = Offset(area.width / 2f, area.height / 2f)
                val r = min(area.width, area.height) / 2f
                val next = StickMath.combine(pointers.map { StickMath.dpadFromOffset(it.position.x - c.x, it.position.y - c.y, r) })
                input.reportPointers("DPAD", pointers.size)
                if (next != dpad) {
                    if (next.hat != Hat.NEUTRAL && next.hat != dpad.hat) haptic()
                    dpad = next
                    input.setDpad(next, t)
                }
            }
    ) {
        val s = size.minDimension
        val c = center
        val arm = s * 0.30f
        val len = s * 0.47f
        drawCircle(skin.centerPanel, radius = s * 0.5f)
        fun arm(angleDeg: Float, active: Boolean) {
            val p = Path().apply {
                moveTo(0f, -len); lineTo(arm / 2, -len); lineTo(arm / 2, -arm * 0.55f)
                lineTo(0f, -arm * 0.12f); lineTo(-arm / 2, -arm * 0.55f); lineTo(-arm / 2, -len); close()
            }
            rotate(angleDeg, c) {
                translate(c.x, c.y) {
                    drawPath(p, if (active) skin.keyPressed else skin.key)
                    drawPath(p, skin.keyEdge, style = Stroke(width = s * if (skin.glow) 0.02f else 0.012f))
                    if (labels) {
                        val tri = Path().apply { moveTo(0f, -len * 0.86f); lineTo(arm * 0.18f, -len * 0.72f); lineTo(-arm * 0.18f, -len * 0.72f); close() }
                        drawPath(tri, if (active) skin.labelPressed else skin.label)
                    }
                }
            }
        }
        val h = dpad.hat
        arm(0f, h == Hat.UP || h == Hat.UP_LEFT || h == Hat.UP_RIGHT)
        arm(90f, h == Hat.RIGHT || h == Hat.UP_RIGHT || h == Hat.DOWN_RIGHT)
        arm(180f, h == Hat.DOWN || h == Hat.DOWN_LEFT || h == Hat.DOWN_RIGHT)
        arm(270f, h == Hat.LEFT || h == Hat.UP_LEFT || h == Hat.DOWN_LEFT)
    }
}

/** Geometry of the face-button diamond for a given spacing (fractions of the cluster size). */
data class FaceGeometry(val offset: Float, val buttonRadius: Float, val hitRadius: Float) {
    companion object {
        /**
         * spacing 1.0 = Phase 2 geometry. The button radius shrinks when spacing grows so buttons
         * stay inside the cluster box; the hit radius keeps the same small overlap between
         * neighbours (adjacent centres are offset*sqrt(2) apart; 2*0.735*offset > that).
         */
        fun of(spacing: Float): FaceGeometry {
            val offset = 0.32f * spacing.coerceIn(0.8f, 1.3f)
            val r = min(0.165f, 0.49f - offset)
            return FaceGeometry(offset, r, offset * 0.735f)
        }
    }
}

/**
 * Triangle / Circle / Cross / Square as one cluster. Each finger presses the button(s) whose hit
 * circle contains it; neighbouring hit circles overlap slightly, so a thumb between two buttons
 * presses both (like rolling a thumb on a real pad), and sliding moves between buttons.
 */
@Composable
fun FaceButtons(input: ControllerInputManager, resetKey: Int, modifier: Modifier = Modifier) {
    var pressedMask by remember(resetKey) { mutableIntStateOf(0) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val style = LocalControlStyle.current
    val geo = remember(style.faceSpacing) { FaceGeometry.of(style.faceSpacing) }
    val haptic = rememberHaptic()
    val order = remember { listOf(ControllerButton.TRIANGLE, ControllerButton.CIRCLE, ControllerButton.CROSS, ControllerButton.SQUARE) }
    val skin = LocalSkin.current
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier
            .onSizeChanged { area = it }
            .inputIfInteractive(resetKey) { pointers, t ->
                val s = min(area.width, area.height).toFloat()
                val centres = faceCentres(Offset(area.width / 2f, area.height / 2f), s, geo)
                val hitR = s * geo.hitRadius
                var mask = 0
                for (p in pointers) for (i in 0 until 4) {
                    val c = centres[i]
                    if (hypot(p.position.x - c.x, p.position.y - c.y) <= hitR) mask = mask or (1 shl i)
                }
                input.reportPointers("FACE", pointers.size)
                if (mask != pressedMask) {
                    if ((mask and pressedMask.inv()) != 0) haptic()
                    for (i in 0 until 4) {
                        val was = (pressedMask and (1 shl i)) != 0
                        val now = (mask and (1 shl i)) != 0
                        if (was != now) input.setButton(order[i], now, t)
                    }
                    pressedMask = mask
                }
            }
    ) {
        val s = size.minDimension
        val centres = faceCentres(center, s, geo)
        val r = s * geo.buttonRadius
        val colors = skin.faceColors
        for (i in 0 until 4) {
            val c = centres[i]
            val on = (pressedMask and (1 shl i)) != 0
            drawCircle(skin.keyEdge, radius = r * 1.06f, center = c)
            val fill = when {
                skin.faceFilled -> if (on) lerp(colors[i], Color.Black, 0.35f) else colors[i]
                on -> colors[i].copy(alpha = 0.35f)
                else -> skin.key
            }
            drawCircle(fill, radius = r, center = c)
            if (skin.glow) drawCircle(colors[i].copy(alpha = if (on) 1f else 0.8f), radius = r * 1.02f, center = c, style = Stroke(r * 0.1f))
            if (!style.showLabels && !on) continue
            val markColor = when {
                skin.faceFilled -> Color.White
                on -> skin.labelPressed
                else -> colors[i]
            }
            if (skin.faceMarks == FaceMarks.LETTERS) {
                val layout = textMeasurer.measure(
                    skin.faceLetters.getOrElse(i) { "" },
                    TextStyle(color = markColor, fontSize = (r * 0.95f).toSp(), fontWeight = FontWeight.Black),
                )
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
                continue
            }
            val stroke = Stroke(width = r * 0.13f, cap = StrokeCap.Round)
            val g = r * 0.46f
            when (i) {
                0 -> drawPath(Path().apply {
                    moveTo(c.x, c.y - g); lineTo(c.x + g * 0.95f, c.y + g * 0.62f); lineTo(c.x - g * 0.95f, c.y + g * 0.62f); close()
                }, markColor, style = stroke)
                1 -> drawCircle(markColor, radius = g * 0.9f, center = c, style = stroke)
                2 -> {
                    drawLine(markColor, Offset(c.x - g * 0.8f, c.y - g * 0.8f), Offset(c.x + g * 0.8f, c.y + g * 0.8f), stroke.width, StrokeCap.Round)
                    drawLine(markColor, Offset(c.x + g * 0.8f, c.y - g * 0.8f), Offset(c.x - g * 0.8f, c.y + g * 0.8f), stroke.width, StrokeCap.Round)
                }
                3 -> drawRect(markColor, topLeft = Offset(c.x - g * 0.75f, c.y - g * 0.75f), size = Size(g * 1.5f, g * 1.5f), style = stroke)
            }
        }
    }
}

/** Triangle (top), Circle (right), Cross (bottom), Square (left). */
private fun faceCentres(c: Offset, s: Float, geo: FaceGeometry): List<Offset> {
    val d = s * geo.offset
    return listOf(Offset(c.x, c.y - d), Offset(c.x + d, c.y), Offset(c.x, c.y + d), Offset(c.x - d, c.y))
}

/**
 * Analog stick. The first finger to land in the area owns it; other fingers in the same area are
 * ignored until the owner lifts. Fixed mode: the centre is the middle of the area plus the
 * calibrated offset. Floating mode: the centre is where the finger landed (kept so the base stays
 * inside the area). Dragging outside the area keeps tracking and saturates at full deflection.
 * Release returns exactly to (0, 0).
 *
 * [onRaw] (calibration screen) gets the finger position relative to the area centre, as a fraction
 * of travel (before dead zone / curve), and the processed output; null when released.
 */
@Composable
fun AnalogStick(
    stick: Stick,
    input: ControllerInputManager,
    resetKey: Int,
    settings: StickSettings,
    modifier: Modifier = Modifier,
    onRaw: ((raw: Offset?, out: StickVector) -> Unit)? = null,
) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    var owner by remember(resetKey) { mutableStateOf<Long?>(null) }
    var origin by remember(resetKey) { mutableStateOf<Offset?>(null) }
    var knob by remember(resetKey) { mutableStateOf(Offset.Zero) } // displacement from origin, px
    var last by remember(resetKey) { mutableStateOf(StickVector.ZERO) }
    val skin = LocalSkin.current

    fun fixedCentre(travel: Float) = Offset(
        area.width / 2f + settings.centerOffsetX * travel,
        area.height / 2f + settings.centerOffsetY * travel,
    )

    Canvas(
        modifier
            .onSizeChanged { area = it }
            .inputIfInteractive(resetKey) { pointers, t ->
                val s = min(area.width, area.height).toFloat()
                val travel = s * STICK_TRAVEL
                input.reportPointers("STICK_$stick", pointers.size)
                var p = pointers.firstOrNull { it.id == owner }
                if (p == null) {
                    p = pointers.firstOrNull()
                    owner = p?.id
                    origin = p?.let {
                        // Current position, not the touch-down point: when a second finger takes
                        // over after the first lifts, the stick must not jump (audit L2).
                        if (settings.floating) Offset(
                            it.position.x.coerceIn(travel, area.width - travel),
                            it.position.y.coerceIn(travel, area.height - travel),
                        ) else fixedCentre(travel)
                    }
                }
                val o = origin
                val v = if (p == null || o == null) {
                    knob = Offset.Zero
                    onRaw?.invoke(null, StickVector.ZERO)
                    StickVector.ZERO
                } else {
                    val dx = p.position.x - o.x
                    val dy = p.position.y - o.y
                    val d = hypot(dx, dy)
                    knob = if (d > travel) Offset(dx / d * travel, dy / d * travel) else Offset(dx, dy)
                    val out = StickMath.process(dx, dy, travel, settings)
                    onRaw?.invoke(Offset((p.position.x - area.width / 2f) / travel, (p.position.y - area.height / 2f) / travel), out)
                    out
                }
                if (p == null) origin = null
                if (v != last) { last = v; input.setStick(stick, v, t) }
            }
    ) {
        val s = size.minDimension
        val travel = s * STICK_TRAVEL
        val c = origin ?: if (settings.floating) center else fixedCentre(travel)
        drawCircle(Color.White.copy(alpha = 0.03f), radius = s / 2)
        drawCircle(skin.keyEdge, radius = travel + s * 0.06f, center = c)
        drawCircle(skin.stickBase, radius = travel + s * 0.045f, center = c)
        if (skin.glow) drawCircle(skin.accent.copy(alpha = 0.7f), radius = travel + s * 0.05f, center = c, style = Stroke(s * 0.012f))
        drawCircle(Color.White.copy(alpha = 0.06f), radius = travel * settings.deadZone, center = c, style = Stroke(s * 0.006f))
        val k = c + knob
        val kr = s * 0.17f
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = kr * 1.08f, center = k + Offset(0f, s * 0.012f))
        drawCircle(
            Brush.radialGradient(listOf(skin.stickKnobLight, skin.stickKnobDark), center = k - Offset(kr * 0.3f, kr * 0.3f), radius = kr * 1.4f),
            radius = kr, center = k,
        )
        drawCircle(skin.stickKnobDark, radius = kr * 0.72f, center = k, style = Stroke(s * 0.012f))
        if (skin.glow) drawCircle(skin.accent, radius = kr, center = k, style = Stroke(s * 0.01f))
        if (owner != null) drawCircle(Color.White.copy(alpha = 0.12f), radius = kr, center = k)
    }
}

const val STICK_TRAVEL = 0.27f // knob travel radius, fraction of the stick area
