package com.ps2gamepad.pc

import com.ps2gamepad.bluetooth.KeyCodes
import com.ps2gamepad.bluetooth.MouseButton
import kotlinx.serialization.Serializable

/**
 * PC Mode data. Completely separate from the emulator profiles/loadouts: PC code never reads or
 * writes emulator settings and vice versa.
 */

@Serializable
enum class PcActionType { KEY, MOUSE, WHEEL_UP, WHEEL_DOWN, GYRO_TOGGLE, GYRO_RECENTER }

/**
 * What a button does. KEY: [key] (HID usage) plus optional [modifiers] (bit mask, e.g. Ctrl+E);
 * a modifier key on its own (e.g. Left Shift) is also a KEY. MOUSE: [mouse] button.
 */
@Serializable
data class PcAction(
    val type: PcActionType = PcActionType.KEY,
    val key: Int = 0,
    val modifiers: Int = 0,
    val mouse: MouseButton = MouseButton.LEFT,
) {
    val label: String get() = when (type) {
        PcActionType.KEY -> buildString {
            for ((bit, n) in MOD_NAMES) if (modifiers and bit != 0) append("$n+")
            append(KeyCodes.name(key))
        }
        PcActionType.MOUSE -> mouse.label
        PcActionType.WHEEL_UP -> "Wheel up"
        PcActionType.WHEEL_DOWN -> "Wheel down"
        PcActionType.GYRO_TOGGLE -> "Gyro on/off"
        PcActionType.GYRO_RECENTER -> "Gyro recenter"
    }

    companion object {
        fun key(usage: Int, modifiers: Int = 0) = PcAction(PcActionType.KEY, key = usage, modifiers = modifiers)
        fun key(name: String) = key(KeyCodes.usage(name))
        fun mouse(b: MouseButton) = PcAction(PcActionType.MOUSE, mouse = b)

        const val MOD_CTRL = 0x01
        const val MOD_SHIFT = 0x02
        const val MOD_ALT = 0x04
        val MOD_NAMES = listOf(MOD_CTRL to "Ctrl", MOD_SHIFT to "Shift", MOD_ALT to "Alt")
    }
}

@Serializable
enum class PcControlKind {
    /** A button with one action. */
    BUTTON,
    /** Left stick sending movement KEYS (default W/A/S/D), 8 directions. */
    STICK_KEYS,
    /** Right stick moving the MOUSE (speed proportional to deflection). */
    STICK_MOUSE,
}

/**
 * One on-screen PC control. Position = centre as a fraction of the screen; [size] multiplies the
 * kind's default size. For STICK_KEYS the four keys are [up], [down], [left], [right].
 */
@Serializable
data class PcControl(
    val id: String,
    val kind: PcControlKind = PcControlKind.BUTTON,
    val label: String,
    val action: PcAction = PcAction(),
    val cx: Float,
    val cy: Float,
    val size: Float = 1f,
    val opacity: Float = 1f,
    /** User-created (can be deleted); defaults can be edited but not deleted. */
    val custom: Boolean = false,
    val up: Int = 0x1A, val down: Int = 0x16, val left: Int = 0x04, val right: Int = 0x07, // W S A D
) {
    fun sanitized() = copy(
        label = label.trim().take(MAX_LABEL).ifEmpty { "Button" },
        cx = cx.finite(0.5f).coerceIn(0f, 1f), cy = cy.finite(0.5f).coerceIn(0f, 1f),
        size = size.finite(1f).coerceIn(0.5f, 2.5f), opacity = opacity.finite(1f).coerceIn(0.15f, 1f),
    )

    companion object { const val MAX_LABEL = 14 }
}

/** Gyroscope mouse + right-stick mouse tuning. */
@Serializable
data class PcMouseSettings(
    val gyroEnabled: Boolean = true,
    /** Overall gyro mouse sensitivity. */
    val gyroSensitivity: Float = 1f,
    val gyroSensitivityX: Float = 1f,
    val gyroSensitivityY: Float = 1f,
    /** Rotation speed (°/s) ignored, so a resting/trembling phone never moves the cursor. */
    val gyroDeadZoneDps: Float = 1.5f,
    /** 0 = raw, 1 = heavy smoothing. */
    val gyroSmoothing: Float = 0.2f,
    val gyroInvertX: Boolean = false,
    val gyroInvertY: Boolean = false,
    /** Right-stick mouse speed at full deflection (pixels / s at Windows default pointer speed, roughly). */
    val stickMouseSpeed: Float = 900f,
    /** Left stick: deflection needed before a movement key is pressed. */
    val moveKeyThreshold: Float = 0.45f,
) {
    fun sanitized() = copy(
        gyroSensitivity = gyroSensitivity.finite(1f).coerceIn(0.1f, 5f),
        gyroSensitivityX = gyroSensitivityX.finite(1f).coerceIn(0.1f, 3f),
        gyroSensitivityY = gyroSensitivityY.finite(1f).coerceIn(0.1f, 3f),
        gyroDeadZoneDps = gyroDeadZoneDps.finite(1.5f).coerceIn(0f, 10f),
        gyroSmoothing = gyroSmoothing.finite(0.2f).coerceIn(0f, 1f),
        stickMouseSpeed = stickMouseSpeed.finite(900f).coerceIn(100f, 4000f),
        moveKeyThreshold = moveKeyThreshold.finite(0.45f).coerceIn(0.15f, 0.9f),
    )
}

@Serializable
data class PcProfile(
    val id: String,
    val name: String,
    val builtIn: Boolean = false,
    val controls: List<PcControl> = PcDefaults.controls(),
    val mouse: PcMouseSettings = PcMouseSettings(),
) {
    fun sanitized() = copy(
        name = name.trim().take(32).ifEmpty { "PC profile" },
        controls = controls.distinctBy { it.id }.map { it.sanitized() },
        mouse = mouse.sanitized(),
    )
}

@Serializable
data class PcStore(
    val activeId: String = PcDefaults.GENERIC,
    val profiles: List<PcProfile> = PcDefaults.profiles(),
) {
    val active: PcProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

object PcDefaults {
    const val GENERIC = "pc_generic"
    const val GTA5 = "pc_gta5"
    const val MAX_CUSTOM_BUTTONS = 12
    const val MAX_PROFILES = 10

    private fun b(id: String, label: String, action: PcAction, cx: Float, cy: Float, size: Float = 1f) =
        PcControl(id, PcControlKind.BUTTON, label, action, cx, cy, size)

    /** The default PC layout from the spec. These are starting points, not universal game controls. */
    fun controls(): List<PcControl> = listOf(
        PcControl("move", PcControlKind.STICK_KEYS, "Move", cx = 0.20f, cy = 0.68f),
        PcControl("look", PcControlKind.STICK_MOUSE, "Look", cx = 0.62f, cy = 0.70f),
        b("sprint", "Sprint", PcAction.key(KeyCodes.L_SHIFT), 0.07f, 0.36f),
        b("crouch", "Crouch", PcAction.key("C"), 0.20f, 0.30f),
        b("a", "A", PcAction.key("Space"), 0.86f, 0.68f),
        b("b", "B", PcAction.key("E"), 0.94f, 0.48f),
        b("x", "X", PcAction.key("R"), 0.78f, 0.48f),
        b("y", "Y", PcAction.key("F"), 0.86f, 0.28f),
        b("lmb", "LMB", PcAction.mouse(MouseButton.LEFT), 0.62f, 0.32f),
        b("rmb", "RMB", PcAction.mouse(MouseButton.RIGHT), 0.71f, 0.32f),
        b("wheel_up", "▲", PcAction(PcActionType.WHEEL_UP), 0.50f, 0.31f, 0.8f),
        b("wheel_down", "▼", PcAction(PcActionType.WHEEL_DOWN), 0.50f, 0.44f, 0.8f),
        b("lb", "LB", PcAction.key("Q"), 0.08f, 0.08f),
        b("lt", "LT", PcAction.key(KeyCodes.L_CTRL), 0.21f, 0.08f),
        b("rb", "RB", PcAction.key("G"), 0.92f, 0.08f),
        b("rt", "RT", PcAction.key(KeyCodes.L_SHIFT), 0.79f, 0.08f),
        b("select", "Select", PcAction.key("Tab"), 0.38f, 0.18f, 0.8f),
        b("start", "Start", PcAction.key("Esc"), 0.47f, 0.18f, 0.8f),
    )

    /** GTA V example: the default layout plus game buttons from the spec. */
    private fun gta5Controls(): List<PcControl> = controls() + listOf(
        PcControl("c_reload", label = "Reload", action = PcAction.key("R"), cx = 0.31f, cy = 0.42f, size = 0.8f, custom = true),
        PcControl("c_map", label = "Map", action = PcAction.key("M"), cx = 0.31f, cy = 0.25f, size = 0.8f, custom = true),
        PcControl("c_phone", label = "Phone", action = PcAction.key("Up"), cx = 0.42f, cy = 0.55f, size = 0.8f, custom = true),
    )

    fun profiles() = listOf(
        PcProfile(GENERIC, "Generic PC Game", builtIn = true),
        PcProfile(GTA5, "GTA V (example)", builtIn = true, controls = gta5Controls()),
    )

    fun byId(id: String) = profiles().firstOrNull { it.id == id }
}

/** Pure PC profile operations (same rules as the emulator ones, separate data). */
object PcProfileManager {
    fun canCreate(s: PcStore) = s.profiles.size < PcDefaults.MAX_PROFILES

    fun select(s: PcStore, id: String) = if (s.profiles.any { it.id == id }) s.copy(activeId = id) else s

    /** New profile = copy of [fromId] (so the user starts from a working layout). */
    fun create(s: PcStore, name: String, fromId: String, idGen: () -> String): PcStore {
        if (!canCreate(s)) return s
        val src = s.profiles.firstOrNull { it.id == fromId } ?: s.active
        val p = src.copy(id = uniqueId(s, idGen), name = uniqueName(s, name.ifBlank { "PC profile" }), builtIn = false).sanitized()
        return s.copy(profiles = s.profiles + p, activeId = p.id)
    }

    fun duplicate(s: PcStore, id: String, idGen: () -> String): PcStore {
        val src = s.profiles.firstOrNull { it.id == id } ?: return s
        return create(s, "${src.name} copy", id, idGen)
    }

    fun rename(s: PcStore, id: String, name: String): PcStore {
        val clean = name.trim().take(32)
        if (clean.isEmpty() || s.profiles.any { it.id != id && it.name.equals(clean, true) }) return s
        return s.copy(profiles = s.profiles.map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** Built-ins cannot be deleted; the last profile cannot be deleted. */
    fun delete(s: PcStore, id: String): PcStore {
        val p = s.profiles.firstOrNull { it.id == id } ?: return s
        if (p.builtIn || s.profiles.size <= 1) return s
        val rest = s.profiles.filter { it.id != id }
        return s.copy(profiles = rest, activeId = if (s.activeId == id) rest.first().id else s.activeId)
    }

    /** Built-in: factory layout. Custom: the default layout and settings (name kept). */
    fun restoreDefaults(s: PcStore, id: String) = s.copy(profiles = s.profiles.map {
        if (it.id != id) it else (PcDefaults.byId(id) ?: PcProfile(id, it.name)).copy(name = it.name, builtIn = it.builtIn)
    })

    fun updateActive(s: PcStore, f: (PcProfile) -> PcProfile) =
        s.copy(profiles = s.profiles.map { if (it.id == s.activeId) f(it).copy(id = it.id, builtIn = it.builtIn).sanitized() else it })

    // ---------------- custom buttons (on the active profile)

    fun customCount(p: PcProfile) = p.controls.count { it.custom }

    fun addCustomButton(s: PcStore, label: String, action: PcAction, idGen: () -> String): PcStore {
        if (customCount(s.active) >= PcDefaults.MAX_CUSTOM_BUTTONS) return s
        val c = PcControl("c_" + idGen(), PcControlKind.BUTTON, label.ifBlank { "Button" }, action, cx = 0.5f, cy = 0.55f, size = 0.8f, custom = true)
        return updateActive(s) { it.copy(controls = it.controls + c) }
    }

    fun updateControl(s: PcStore, id: String, f: (PcControl) -> PcControl) =
        updateActive(s) { p -> p.copy(controls = p.controls.map { if (it.id == id) f(it).copy(id = it.id, kind = it.kind, custom = it.custom) else it }) }

    /** Only custom buttons can be deleted (defaults can be moved/hidden via opacity/remapped). */
    fun deleteControl(s: PcStore, id: String) =
        updateActive(s) { p -> p.copy(controls = p.controls.filterNot { it.id == id && it.custom }) }

    fun normalize(s: PcStore?): PcStore {
        if (s == null || s.profiles.isEmpty()) return PcStore()
        val seen = HashSet<String>()
        val profiles = s.profiles.filter { it.id.isNotBlank() && seen.add(it.id) }.map { it.sanitized() }.toMutableList()
        for (b in PcDefaults.profiles()) if (profiles.none { it.id == b.id }) profiles.add(b)
        return PcStore(if (profiles.any { it.id == s.activeId }) s.activeId else PcDefaults.GENERIC, profiles)
    }

    private fun uniqueId(s: PcStore, idGen: () -> String): String {
        var id = idGen()
        while (s.profiles.any { it.id == id }) id = idGen()
        return id
    }

    private fun uniqueName(s: PcStore, base: String): String {
        val b = base.trim().take(28)
        if (s.profiles.none { it.name.equals(b, true) }) return b
        var i = 2
        while (s.profiles.any { it.name.equals("$b $i", true) }) i++
        return "$b $i"
    }
}

private fun Float.finite(d: Float) = if (isFinite()) this else d
