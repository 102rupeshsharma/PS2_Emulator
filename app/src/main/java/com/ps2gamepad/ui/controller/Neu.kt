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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.ps2gamepad.model.ButtonStyle
import com.ps2gamepad.model.FaceLabels

/**
 * Controller colours. The background is always pure black: on AMOLED screens black pixels are
 * switched off, so it is the lowest-power background possible. Only the buttons light up.
 */
data class NeuTheme(
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
    val BLACK = NeuTheme(
        bg = Color.Black,
        key = Color(0xFF15171C), ring = Color(0x66C8CFDA),
        pressed = Color(0xFF2B303A), pressedRing = Color(0xFFFFFFFF),
        label = Color(0xFFF2F4F8), labelDim = Color(0xFF8E96A3),
        accent = Color(0xFF3B82F6), chip = Color(0xFF16181D), panel = Color(0xF20E1014),
        text = Color(0xFFF2F4F8), textDim = Color(0xFF9AA1AD),
    )
}

/**
 * Face button colours, index 0..3 = top, right, bottom, left (= Triangle, Circle, Cross, Square,
 * which are HID buttons 4, 3, 2, 1 whatever the labels say).
 */
object FaceColors {
    private val XBOX = listOf(Color(0xFFE9D44C), Color(0xFFE5484D), Color(0xFF6CC24A), Color(0xFF4DA3E8)) // Y B A X
    private val PLAYSTATION = listOf(Color(0xFF3FD18B), Color(0xFFFF5A5F), Color(0xFF7FB2FF), Color(0xFFF08CD0)) // triangle circle cross square
    private val LETTERS = listOf("Y", "B", "A", "X")
    private val SYMBOLS = listOf("△", "○", "✕", "□")

    fun color(labels: FaceLabels, i: Int): Color = (if (labels == FaceLabels.XBOX) XBOX else PLAYSTATION)[i]
    fun text(labels: FaceLabels, i: Int): String = (if (labels == FaceLabels.XBOX) LETTERS else SYMBOLS)[i]
}

val LocalNeu = staticCompositionLocalOf { NeuThemes.BLACK }

/** Inset used by controls so their ring / 3D edge stays inside their box. */
fun neuDepth(size: Size) = (size.minDimension * 0.05f).coerceIn(2f, 8f)

/**
 * Draws one control shape in the chosen [style]:
 *  - REAL: a 3D key (lit from above: light top, dark bottom, glossy highlight, dark rim); pressed
 *    keys flip the light and sink a little, like a real button travelling down.
 *  - SIMPLE: flat dark fill with a thin ring; pressed = lighter fill + bright ring.
 *  - OUTLINE: ring only (nothing else lights up); pressed = faint fill.
 * [accent] (face buttons) colours the ring in SIMPLE/OUTLINE; REAL keeps neutral keys with
 * coloured symbols, like a real pad.
 */
fun DrawScope.neuPath(
    path: Path, t: NeuTheme, pressed: Boolean, depth: Float,
    fill: Color = t.key, accent: Color? = null, style: ButtonStyle = ButtonStyle.SIMPLE,
) {
    val ringW = (depth * 0.55f).coerceIn(2f, 5f)
    when (style) {
        ButtonStyle.REAL -> {
            val b = path.getBounds()
            val sink = if (pressed) depth * 0.35f else 0f
            translate(0f, sink) {
                // body: vertical light gradient (flipped when pressed)
                val top = if (pressed) Color(0xFF16181D) else Color(0xFF474C57)
                val bottom = if (pressed) Color(0xFF2A2E36) else Color(0xFF15171B)
                drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = b.top, endY = b.bottom))
                // glossy highlight on the upper half
                if (!pressed) drawPath(path, Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = b.top, endY = b.top + b.height * 0.55f,
                ))
                // rims: dark outside edge + thin light bevel
                drawPath(path, Color.Black.copy(alpha = 0.9f), style = Stroke(width = ringW * 1.2f))
                drawPath(path, Color.White.copy(alpha = if (pressed) 0.12f else 0.22f), style = Stroke(width = ringW * 0.5f))
                if (pressed && accent != null) drawPath(path, accent.copy(alpha = 0.18f))
            }
        }
        ButtonStyle.SIMPLE -> {
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
        ButtonStyle.OUTLINE -> {
            if (pressed) drawPath(path, (accent ?: Color.White).copy(alpha = 0.22f))
            val ring = accent ?: if (pressed) t.pressedRing else t.ring.copy(alpha = 0.75f)
            drawPath(path, ring, style = Stroke(width = if (pressed) ringW * 1.5f else ringW))
        }
    }
}

fun circlePath(center: Offset, radius: Float) = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
}

fun roundRectPath(topLeft: Offset, size: Size, radius: Float) = Path().apply {
    addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, CornerRadius(radius)))
}

/** Whole-screen background: pure black. */
@Composable
fun NeuScreenBackground(t: NeuTheme, modifier: Modifier) {
    Box(modifier.drawBehind { drawRect(t.bg) })
}
