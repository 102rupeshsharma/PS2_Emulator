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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Controller look (2.1.0): "glass" controls on a pure black background, for both controllers.
 * Each control is a dark fill with a thin light ring; pressed = lighter fill + bright ring.
 * Black pixels are switched off on AMOLED screens, so this is also the lowest-power look.
 *
 * (The type keeps its 1.8 name so all controls and screens use it unchanged.)
 */
data class NeuTheme(
    val dark: Boolean,
    val bg: Color,
    val key: Color,
    val ring: Color,
    val pressed: Color,
    val pressedRing: Color,
    val label: Color,
    val labelDim: Color,
    val accent: Color,
    val chip: Color,
    val panel: Color,
    val text: Color,
    val textDim: Color,
)

object NeuThemes {
    val GLASS = NeuTheme(
        dark = true,
        bg = Color.Black,
        key = Color(0xFF15171C), ring = Color(0x66C8CFDA),
        pressed = Color(0xFF2B303A), pressedRing = Color(0xFFFFFFFF),
        label = Color(0xFFF2F4F8), labelDim = Color(0xFF8E96A3),
        accent = Color(0xFF3B82F6), chip = Color(0xFF16181D), panel = Color(0xF20E1014),
        text = Color(0xFFF2F4F8), textDim = Color(0xFF9AA1AD),
    )

    /** Kept for older call sites and saved settings: every value gives the one black look. */
    val LIGHT = GLASS
    val DARK = GLASS
    @Suppress("UNUSED_PARAMETER") fun of(dark: Boolean) = GLASS
}

/** Face button colours (top, right, bottom, left) = Y yellow, B red, A green, X blue. */
object FaceColors {
    val Y = Color(0xFFE9D44C)
    val B = Color(0xFFE5484D)
    val A = Color(0xFF6CC24A)
    val X = Color(0xFF4DA3E8)
    val LETTERS = listOf("Y", "B", "A", "X")
    val COLORS = listOf(Y, B, A, X)

    /** Colour for a PC button labelled A/B/X/Y, else null. */
    fun forLabel(label: String): Color? = when (label.trim().uppercase()) { "A" -> A; "B" -> B; "X" -> X; "Y" -> Y; else -> null }
}

val LocalNeu = staticCompositionLocalOf { NeuThemes.GLASS }

/** Inset used by controls so the ring stays inside their box. */
fun neuDepth(size: Size) = (size.minDimension * 0.05f).coerceIn(2f, 8f)

/**
 * Draws [path] as a glass control: dark fill + thin ring; pressed = lighter fill + bright ring.
 * [accent] (face buttons) colours the ring and tints the pressed fill.
 */
fun DrawScope.neuPath(path: Path, t: NeuTheme, pressed: Boolean, depth: Float, fill: Color = t.key, accent: Color? = null) {
    val ringW = (depth * 0.55f).coerceIn(2f, 5f)
    val body = when {
        pressed && accent != null -> accent.copy(alpha = 0.32f)
        pressed -> t.pressed
        else -> fill
    }
    drawPath(path, body)
    val ring = when {
        accent != null -> accent.copy(alpha = if (pressed) 1f else 0.9f)
        pressed -> t.pressedRing
        else -> t.ring
    }
    drawPath(path, ring, style = Stroke(width = if (pressed) ringW * 1.4f else ringW))
}

fun circlePath(center: Offset, radius: Float) = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
}

fun roundRectPath(topLeft: Offset, size: Size, radius: Float) = Path().apply {
    addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, CornerRadius(radius)))
}

/** Glass rounded rectangle behind a composable's content. */
fun Modifier.neuBackground(t: NeuTheme, pressed: Boolean, cornerFraction: Float = 0.3f, accent: Color? = null): Modifier = drawBehind {
    val d = neuDepth(size)
    val inset = d * 0.8f
    val s = Size(size.width - 2 * inset, size.height - 2 * inset)
    neuPath(roundRectPath(Offset(inset, inset), s, s.minDimension * cornerFraction), t, pressed, d, accent = accent)
}

/** Whole-screen background: pure black. */
@Composable
fun NeuScreenBackground(t: NeuTheme, modifier: Modifier) {
    Box(modifier.drawBehind { drawRect(t.bg) })
}
