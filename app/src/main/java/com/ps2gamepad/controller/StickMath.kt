package com.ps2gamepad.controller

import com.ps2gamepad.model.Dpad
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.StickSettings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/** Normalised stick vector, each component in [-1, 1]. Y positive = down (screen and HID agree). */
data class StickVector(val x: Float, val y: Float) {
    companion object { val ZERO = StickVector(0f, 0f) }
}

object StickMath {

    /**
     * Converts a finger displacement from the stick centre (pixels) into a stick vector.
     *  1. Normalise by the travel radius.
     *  2. Radial dead zone: inside it the output is exactly 0 (no drift).
     *  3. Rescale [deadZone, maxRange] to [0, 1], so there is no jump at the dead-zone edge and
     *     anything past maxRange (or outside the stick) is full deflection, keeping its direction.
     *  4. Response curve t^curve, then sensitivity, then clamp to 1.
     *  5. Optional Y inversion.
     * The result always has magnitude <= 1, so it can never leave the HID logical range.
     */
    fun process(dx: Float, dy: Float, radius: Float, settings: StickSettings): StickVector {
        if (radius <= 0f || !dx.isFinite() || !dy.isFinite()) return StickVector.ZERO
        val cfg = settings.sanitized()
        val nx = dx / radius
        val ny = dy / radius
        val mag = hypot(nx, ny)
        if (mag <= cfg.deadZone || mag == 0f) return StickVector.ZERO
        val t = ((mag.coerceAtMost(cfg.maxRange) - cfg.deadZone) / (cfg.maxRange - cfg.deadZone)).coerceIn(0f, 1f)
        val out = (t.pow(cfg.curve) * cfg.sensitivity).coerceIn(0f, 1f)
        val y = ny / mag * out
        return StickVector(nx / mag * out, if (cfg.invertY) -y else y)
    }

    /** [-1, 1] -> HID axis [-127, 127]. Symmetric, with 0 exactly at centre. */
    fun toAxis(v: Float): Int {
        if (v.isNaN()) return 0
        // Round the magnitude, then reapply the sign: plain rounding is biased (-63.5 -> -63, 63.5 -> 64).
        val mag = (abs(v.coerceIn(-1f, 1f)) * GamepadState.AXIS_MAX).roundToInt()
        return if (v < 0) -mag else mag
    }

    /**
     * D-pad direction for a finger at (dx, dy) from the D-pad centre, as fractions of its radius.
     * Within [innerDeadZone] nothing is pressed. Otherwise a direction is active when its
     * component exceeds [threshold] times the distance. 0.38 gives eight equal 45 degree sectors;
     * the default 0.42 makes the four cardinal sectors a little wider than the diagonals.
     */
    fun dpadFromOffset(dx: Float, dy: Float, radius: Float, innerDeadZone: Float = 0.18f, threshold: Float = 0.42f): Dpad {
        if (radius <= 0f) return Dpad.NEUTRAL
        val nx = dx / radius
        val ny = dy / radius
        val dist = hypot(nx, ny)
        if (dist < innerDeadZone) return Dpad.NEUTRAL
        val t = threshold * dist
        return Dpad(up = ny < -t, down = ny > t, left = nx < -t, right = nx > t)
    }

    /** Union of several fingers on the D-pad. Opposite directions cancel later, in [com.ps2gamepad.model.Hat.from]. */
    fun combine(dpads: Iterable<Dpad>): Dpad = dpads.fold(Dpad.NEUTRAL) { a, b ->
        Dpad(a.up || b.up, a.down || b.down, a.left || b.left, a.right || b.right)
    }

    /** True when the change is worth a new report: at least [threshold] counts, or a snap to centre/edge. */
    fun axisChangedSignificantly(old: Int, new: Int, threshold: Int): Boolean {
        if (old == new) return false
        if (new == 0 || abs(new) == GamepadState.AXIS_MAX) return true
        return abs(new - old) >= threshold
    }
}
