package com.ps2gamepad.ui.controller

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * Soft UI ("neumorphism") for both controllers (1.8.0): every control has the background colour
 * and is shaped only by a light shadow (top-left) and a dark shadow (bottom-right). Pressed
 * controls look pushed in (inner shadows). Two themes: light (default) and black.
 */
data class NeuTheme(
    val dark: Boolean,
    val bg: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val pressed: Color,
    val label: Color,
    val labelDim: Color,
    val accent: Color,
    val chip: Color,
    val panel: Color,
    val text: Color,
    val textDim: Color,
)

object NeuThemes {
    val LIGHT = NeuTheme(
        dark = false,
        bg = Color(0xFFE3E8EF), shadowLight = Color(0xFFFFFFFF), shadowDark = Color(0xFFA7B3C6),
        pressed = Color(0xFFDCE2EA), label = Color(0xFF3B4766), labelDim = Color(0xFF7B87A1),
        accent = Color(0xFFE5484D), chip = Color(0xFFD5DCE6), panel = Color(0xFAEAEEF4),
        text = Color(0xFF26304A), textDim = Color(0xFF66718C),
    )
    val DARK = NeuTheme(
        dark = true,
        bg = Color(0xFF1F2227), shadowLight = Color(0xFF30343B), shadowDark = Color(0xFF0E0F12),
        pressed = Color(0xFF1B1D21), label = Color(0xFFD9DEE7), labelDim = Color(0xFF8D95A3),
        accent = Color(0xFFE5484D), chip = Color(0xFF2A2E35), panel = Color(0xFA24282E),
        text = Color(0xFFE9ECF2), textDim = Color(0xFF9CA3B0),
    )

    fun of(dark: Boolean) = if (dark) DARK else LIGHT
}

val LocalNeu = staticCompositionLocalOf { NeuThemes.LIGHT }

/** Shadow offset for a control of [size]: proportional, but never invisible or huge. */
fun neuDepth(size: Size) = (size.minDimension * 0.07f).coerceIn(3f, 14f)

/**
 * Draws [path] as a soft-UI shape: raised (outer light + dark shadows) or, when [pressed], pushed in
 * (darker fill + inner shadows). Shadows are drawn outside the path, so callers inset the path by
 * about [depth] to keep them on screen.
 *
 * 1.9.0: the soft edge is built from a few translucent strokes instead of BlurMaskFilter. A blur
 * mask is not drawn the same way by every phone's GPU (it can come out as hard-edged boxes); plain
 * strokes look identical on every device and in the screenshot tests.
 */
fun DrawScope.neuPath(path: Path, t: NeuTheme, pressed: Boolean, depth: Float, fill: Color = t.bg) {
    if (!pressed) {
        softShadow(path, t.shadowDark, Offset(depth, depth), depth * 1.6f, 0.55f)
        softShadow(path, t.shadowLight, Offset(-depth, -depth), depth * 1.6f, if (t.dark) 0.7f else 0.95f)
        drawPath(path, fill)
    } else {
        drawPath(path, t.pressed)
        clipPath(path) {
            softShadow(path, t.shadowDark, Offset(depth * 0.7f, depth * 0.7f), depth * 1.2f, 0.6f, inner = true)
            softShadow(path, t.shadowLight, Offset(-depth * 0.7f, -depth * 0.7f), depth * 1.2f, if (t.dark) 0.5f else 0.8f, inner = true)
        }
    }
}

/**
 * A soft shadow of [path] shifted by [offset]: [STEPS] strokes from wide/faint to narrow, plus the
 * filled shape for an outer shadow. Total opacity at the edge is about [strength].
 */
private fun DrawScope.softShadow(path: Path, color: Color, offset: Offset, spread: Float, strength: Float, inner: Boolean = false) {
    val a = strength / STEPS
    translate(offset.x, offset.y) {
        for (i in STEPS downTo 1) {
            drawPath(path, color.copy(alpha = color.alpha * a), style = Stroke(width = spread * 2f * i / STEPS, join = StrokeJoin.Round))
        }
        if (!inner) drawPath(path, color.copy(alpha = color.alpha * strength))
    }
}

private const val STEPS = 6

fun circlePath(center: Offset, radius: Float) = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
}

fun roundRectPath(topLeft: Offset, size: Size, radius: Float) = Path().apply {
    addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, CornerRadius(radius)))
}

/** Raised (or pressed-in) rounded rectangle behind a composable's content, inset so its shadows fit. */
fun Modifier.neuBackground(t: NeuTheme, pressed: Boolean, cornerFraction: Float = 0.3f): Modifier = drawBehind {
    val d = neuDepth(size)
    val inset = d * 0.8f
    val s = Size(size.width - 2 * inset, size.height - 2 * inset)
    neuPath(roundRectPath(Offset(inset, inset), s, s.minDimension * cornerFraction), t, pressed, d)
}

/** Whole-screen soft background (replaces the old controller body art). */
@Composable
fun NeuScreenBackground(t: NeuTheme, modifier: Modifier) {
    Box(modifier.drawBehind { drawRect(t.bg) })
}
