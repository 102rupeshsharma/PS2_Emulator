package com.ps2gamepad.controller

import com.ps2gamepad.model.Appearance
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.model.Placement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A control's box in dp, by centre and size. */
data class ControlRect(val cx: Float, val cy: Float, val w: Float, val h: Float) {
    val left get() = cx - w / 2
    val right get() = cx + w / 2
    val top get() = cy - h / 2
    val bottom get() = cy + h / 2

    /** True when the boxes overlap by more than [tolerance] dp on both axes. */
    fun overlaps(o: ControlRect, tolerance: Float = 1f): Boolean =
        abs(cx - o.cx) < (w + o.w) / 2 - tolerance && abs(cy - o.cy) < (h + o.h) / 2 - tolerance
}

/**
 * Pure layout maths (no Compose), so it can be unit tested for every screen size.
 *
 * Default layout (unchanged from Phase 2): unit u = min(height, width / 2.4); the left and right
 * clusters hang off their screen edges, Start/Select sit in the middle. Appearance scales the
 * default sizes (and the shoulder stacking) around the same anchors. User placements store the
 * centre as a fraction of the screen plus a size multiplier, so they adapt to other screens.
 */
object LayoutEngine {

    fun unit(w: Float, h: Float) = min(h, w / 2.4f)

    fun defaults(w: Float, h: Float, a: Appearance = Appearance()): Map<ControlId, ControlRect> {
        val u = unit(w, h)
        val m = u * 0.04f
        val bs = a.buttonScale
        val ss = a.stickScale
        val shW = u * 0.42f * bs
        val shH = u * 0.13f * bs
        val shX = m + shW / 2 + u * 0.05f
        val l2y = shH / 2 + u * 0.01f + h * 0.02f
        val l1y = l2y + shH + u * 0.02f
        val dpad = u * 0.46f * bs
        val face = u * 0.50f * bs
        val clusterX = m + max(dpad, face) / 2 + u * 0.04f
        val clusterY = l1y + shH / 2 + u * 0.04f + max(dpad, face) / 2
        val stick = u * 0.50f * ss
        // Anchored to the cluster's outer edge so sticks never slide into the D-pad / face buttons
        // when buttons and sticks are scaled differently (at scale 1 this is the Phase 2 position + 0.04u).
        val stickX = clusterX + max(dpad, face) / 2 + u * 0.02f + stick / 2
        val stickY = h - stick / 2 - u * 0.02f
        val l3 = u * 0.15f * bs
        // L3/R3 sit just above their stick (thumb slides up to click), between the shoulders and the stick.
        val l3X = stickX - stick / 2 + l3 / 2
        val l3Y = (l1y + shH / 2 + (stickY - stick / 2)) / 2
        val ssW = u * 0.19f * bs
        val ssH = u * 0.085f * bs
        val ssY = h * 0.36f
        val faceBox = ControlRect(w - clusterX, clusterY, face, face)
        return faceButtons(faceBox, a.buttonSpacing) + mapOf(
            ControlId.L2 to ControlRect(shX, l2y, shW, shH),
            ControlId.L1 to ControlRect(shX, l1y, shW, shH),
            ControlId.R2 to ControlRect(w - shX, l2y, shW, shH),
            ControlId.R1 to ControlRect(w - shX, l1y, shW, shH),
            ControlId.DPAD to ControlRect(clusterX, clusterY, dpad, dpad),
            ControlId.FACE to faceBox,
            ControlId.LEFT_STICK to ControlRect(stickX, stickY, stick, stick),
            ControlId.RIGHT_STICK to ControlRect(w - stickX, stickY, stick, stick),
            ControlId.L3 to ControlRect(l3X, l3Y, l3, l3),
            ControlId.R3 to ControlRect(w - l3X, l3Y, l3, l3),
            ControlId.SELECT to ControlRect(w / 2 - u * 0.12f * bs, ssY, ssW, ssH),
            ControlId.START to ControlRect(w / 2 + u * 0.12f * bs, ssY, ssW, ssH),
        )
    }

    /**
     * The four face buttons (1.9.0: separate controls) laid out inside a cluster box, in the same
     * diamond as before: triangle top, circle right, cross bottom, square left.
     */
    fun faceButtons(box: ControlRect, spacing: Float = 1f): Map<ControlId, ControlRect> {
        val s = min(box.w, box.h)
        val offset = 0.32f * spacing.coerceIn(0.8f, 1.3f)
        val r = min(0.165f, 0.49f - offset)
        val d = s * r * 2f * 1.18f // button diameter + room for its soft shadow
        val o = s * offset
        return mapOf(
            ControlId.TRIANGLE to ControlRect(box.cx, box.cy - o, d, d),
            ControlId.CIRCLE to ControlRect(box.cx + o, box.cy, d, d),
            ControlId.CROSS to ControlRect(box.cx, box.cy + o, d, d),
            ControlId.SQUARE to ControlRect(box.cx - o, box.cy, d, d),
        )
    }

    /**
     * Final layout: defaults, then user placements (centre fraction + size multiplier), kept on screen.
     * A face button without its own placement follows the legacy cluster placement (old layouts).
     */
    fun resolve(w: Float, h: Float, a: Appearance, placements: Map<ControlId, Placement>): Map<ControlId, ControlRect> {
        val defaults = defaults(w, h, a)
        fun place(id: ControlId, d: ControlRect): ControlRect {
            val p = placements[id] ?: return clampInside(d, w, h)
            return clampInside(ControlRect(p.cx * w, p.cy * h, d.w * p.scale, d.h * p.scale), w, h)
        }
        val out = defaults.mapValues { (id, d) -> place(id, d) }.toMutableMap()
        val legacyFace = faceButtons(out.getValue(ControlId.FACE), a.buttonSpacing)
        for (id in ControlId.FACE_BUTTONS) {
            if (placements[id] == null) out[id] = clampInside(legacyFace.getValue(id), w, h)
        }
        return out
    }

    /** Moves (never resizes, unless bigger than the screen) a rect so it is fully visible. */
    fun clampInside(r: ControlRect, w: Float, h: Float): ControlRect {
        val rw = min(r.w, w); val rh = min(r.h, h)
        return ControlRect(r.cx.coerceIn(rw / 2, w - rw / 2), r.cy.coerceIn(rh / 2, h - rh / 2), rw, rh)
    }

    /**
     * All pairs of visible controls that overlap (the legacy FACE box is not drawn, so it is
     * ignored). Two face buttons are compared as the circles they are. Overlap is allowed since
     * 1.9.0; this is used to check that the DEFAULT layout is clean.
     */
    fun overlaps(layout: Map<ControlId, ControlRect>): List<Pair<ControlId, ControlId>> {
        val ids = layout.keys.filter { it != ControlId.FACE }
        val out = mutableListOf<Pair<ControlId, ControlId>>()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            val a = layout.getValue(ids[i]); val b = layout.getValue(ids[j])
            val hit = if (ids[i] in ControlId.FACE_BUTTONS && ids[j] in ControlId.FACE_BUTTONS) {
                kotlin.math.hypot(a.cx - b.cx, a.cy - b.cy) < (a.w + b.w) / 2 - 1f
            } else a.overlaps(b)
            if (hit) out += ids[i] to ids[j]
        }
        return out
    }

    /** Controls that [id] would overlap if placed at [rect]. Empty = the drop is allowed. */
    fun collisions(layout: Map<ControlId, ControlRect>, id: ControlId, rect: ControlRect): List<ControlId> =
        layout.filter { it.key != id && it.value.overlaps(rect) }.keys.toList()

    fun toPlacement(r: ControlRect, default: ControlRect, w: Float, h: Float) =
        Placement(r.cx / w, r.cy / h, if (default.w > 0f) r.w / default.w else 1f).sanitized()
}
