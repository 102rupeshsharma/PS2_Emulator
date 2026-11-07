package com.ps2gamepad.model

import kotlinx.serialization.Serializable

/**
 * Everything the user can customise. Profiles only change layout, look and analog behaviour.
 * They never change the HID descriptor or which HID button a control sends.
 */

/** Movable controls in the layout editor. Each maps to fixed HID usages; that mapping is not editable. */
@Serializable
enum class ControlId(val label: String) {
    L2("L2"), L1("L1"), R2("R2"), R1("R1"),
    DPAD("D-pad"),
    /** Legacy (before 1.9.0): the four face buttons as one cluster. Kept so old layouts still load;
     *  its position/size is now only the starting point of the four separate buttons below. */
    FACE("Face buttons"),
    LEFT_STICK("Left stick"), RIGHT_STICK("Right stick"),
    L3("L3"), R3("R3"), SELECT("Select"), START("Start"),
    TRIANGLE("Triangle"), CIRCLE("Circle"), CROSS("Cross"), SQUARE("Square");

    companion object {
        /** Face buttons in cluster order: top, right, bottom, left. */
        val FACE_BUTTONS = listOf(TRIANGLE, CIRCLE, CROSS, SQUARE)
        /** Controls that are drawn and can be moved (everything except the legacy cluster). */
        val editable: List<ControlId> = entries.filter { it != FACE }
    }
}

@Serializable
enum class ControllerBackground(val label: String) { DS2_BODY("Controller body"), DARK("Dark"), BLACK("Black (OLED)"), BLUE("Midnight blue") }

@Serializable
data class StickSettings(
    /** Radial dead zone, fraction of travel. Output is exactly 0 inside it. */
    val deadZone: Float = 0.10f,
    /** Multiplier after dead zone / curve. */
    val sensitivity: Float = 1.0f,
    /** Travel fraction at which output reaches 100 % (outer dead zone). 1 = full travel. */
    val maxRange: Float = 1.0f,
    /** Response curve exponent: 1 = linear, > 1 finer control near centre, < 1 quicker start. */
    val curve: Float = 1.0f,
    val invertY: Boolean = false,
    /** Floating: centre where the thumb lands. Fixed: centre of the stick (+ calibration). */
    val floating: Boolean = true,
    /** Fixed-mode centre offset, fraction of travel. Set by the calibration screen. */
    val centerOffsetX: Float = 0f,
    val centerOffsetY: Float = 0f,
) {
    /** Clamps every field into its safe range (protects against corrupt or old saved data). */
    fun sanitized(): StickSettings {
        val dz = deadZone.finiteOr(0.10f).coerceIn(0f, 0.5f)
        return StickSettings(
        deadZone = dz,
        sensitivity = sensitivity.finiteOr(1f).coerceIn(0.25f, 3f),
        // max range must stay clearly above the dead zone so the response never divides by ~0
        maxRange = maxRange.finiteOr(1f).coerceIn(maxOf(0.5f, dz + 0.1f), 1f),
        curve = curve.finiteOr(1f).coerceIn(MIN_CURVE, 3f),
        invertY = invertY,
        floating = floating,
        centerOffsetX = centerOffsetX.finiteOr(0f).coerceIn(-MAX_CENTER_OFFSET, MAX_CENTER_OFFSET),
        centerOffsetY = centerOffsetY.finiteOr(0f).coerceIn(-MAX_CENTER_OFFSET, MAX_CENTER_OFFSET),
        )
    }

    companion object {
        const val MAX_CENTER_OFFSET = 0.5f
        /** Below ~0.6 the curve is so steep that 1 % past the dead zone gives > 10 % output (a jump). */
        const val MIN_CURVE = 0.6f
    }
}

@Serializable
data class Appearance(
    /** Size multiplier for digital buttons (D-pad, face cluster, shoulders, Start/Select, L3/R3). */
    val buttonScale: Float = 1.0f,
    val stickScale: Float = 1.0f,
    /** Opacity of the controls. */
    val buttonOpacity: Float = 1.0f,
    /** Opacity of the controller body / background art. */
    val controllerOpacity: Float = 1.0f,
    /** Spacing between the four face buttons. */
    val buttonSpacing: Float = 1.0f,
    val background: ControllerBackground = ControllerBackground.DS2_BODY,
    val showLabels: Boolean = true,
    val haptics: Boolean = true,
    /** Extra opacity per control (1 = fully visible), applied on top of [buttonOpacity]. */
    val controlOpacity: Map<ControlId, Float> = emptyMap(),
) {
    fun opacityOf(id: ControlId): Float =
        controlOpacity[id] ?: (if (id in ControlId.FACE_BUTTONS) controlOpacity[ControlId.FACE] else null) ?: 1f

    fun sanitized() = copy(
        buttonScale = buttonScale.finiteOr(1f).coerceIn(0.6f, 1.6f),
        stickScale = stickScale.finiteOr(1f).coerceIn(0.6f, 1.6f),
        buttonOpacity = buttonOpacity.finiteOr(1f).coerceIn(0.2f, 1f),
        controllerOpacity = controllerOpacity.finiteOr(1f).coerceIn(0f, 1f),
        buttonSpacing = buttonSpacing.finiteOr(1f).coerceIn(0.8f, 1.3f),
        controlOpacity = controlOpacity.mapValues { it.value.finiteOr(1f).coerceIn(MIN_CONTROL_OPACITY, 1f) }.filterValues { it < 1f },
    )

    companion object {
        /** Never fully invisible: a control you cannot see is a control you hit by accident. */
        const val MIN_CONTROL_OPACITY = 0.1f
    }
}

/**
 * A control moved/resized by the user. Centre is a fraction of the screen (0..1) so layouts
 * survive different screen sizes; [scale] multiplies the control's default size.
 */
@Serializable
data class Placement(val cx: Float, val cy: Float, val scale: Float = 1f) {
    fun sanitized() = Placement(cx.finiteOr(0.5f).coerceIn(0f, 1f), cy.finiteOr(0.5f).coerceIn(0f, 1f), scale.finiteOr(1f).coerceIn(0.5f, 2f))
}

/**
 * A button the user added in the layout editor (1.7.0). It sends one of the 12 gamepad buttons, so
 * the HID report never changes: it is a second place to press e.g. Cross or R3. Centre is a screen
 * fraction (0..1); [scale] multiplies the default extra-button size.
 */
@Serializable
data class ExtraButton(
    val id: String,
    val button: ControllerButton,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    val scale: Float = 1f,
) {
    fun sanitized() = copy(
        cx = cx.finiteOr(0.5f).coerceIn(0f, 1f),
        cy = cy.finiteOr(0.5f).coerceIn(0f, 1f),
        scale = scale.finiteOr(1f).coerceIn(0.5f, 2f),
    )
}

/**
 * Gyroscope right stick: tilting the phone moves the right analog stick like a physical stick
 * (position-based: hold a tilt and the stick stays displaced; back to neutral and it centres).
 */
@Serializable
data class GyroSettings(
    val enabled: Boolean = false,
    /** Output multiplier after the dead zone. */
    val sensitivity: Float = 1.0f,
    /** Tilt (degrees from neutral) that gives full deflection. */
    val maxTiltDeg: Float = 25f,
    /** Tilt (degrees) ignored around neutral, so hand tremor never moves the camera. */
    val deadZoneDeg: Float = 2f,
    /** 0 = raw, 1 = heavy smoothing (time constant up to 0.2 s). */
    val smoothing: Float = 0.25f,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
) {
    fun sanitized(): GyroSettings {
        val max = maxTiltDeg.finiteOr(25f).coerceIn(MIN_TILT, MAX_TILT)
        return copy(
            sensitivity = sensitivity.finiteOr(1f).coerceIn(0.25f, 3f),
            maxTiltDeg = max,
            deadZoneDeg = deadZoneDeg.finiteOr(2f).coerceIn(0f, minOf(MAX_DEAD_ZONE, max * 0.5f)),
            smoothing = smoothing.finiteOr(0.25f).coerceIn(0f, 1f),
        )
    }

    companion object {
        const val MIN_TILT = 5f
        const val MAX_TILT = 60f
        const val MAX_DEAD_ZONE = 10f
    }
}

/** Calibrated neutral hold: gravity direction in SCREEN coordinates (unit vector). */
@Serializable
data class GyroNeutral(val x: Float, val y: Float, val z: Float)

@Serializable
data class ControllerProfile(
    val id: String,
    val name: String,
    /** Built-in profiles can be restored to defaults and cannot be deleted. */
    val builtIn: Boolean = false,
    val appearance: Appearance = Appearance(),
    val left: StickSettings = StickSettings(),
    val right: StickSettings = StickSettings(),
    /** Only controls the user moved; everything else uses the default layout. */
    val placements: Map<ControlId, Placement> = emptyMap(),
    val gyro: GyroSettings = GyroSettings(),
    /** Buttons added by the user, each mapped to a gamepad button. */
    val extraButtons: List<ExtraButton> = emptyList(),
) {
    fun sanitized() = copy(
        name = name.trim().take(MAX_NAME).ifEmpty { "Profile" },
        appearance = appearance.sanitized(),
        left = left.sanitized(),
        right = right.sanitized(),
        placements = placements.mapValues { it.value.sanitized() },
        gyro = gyro.sanitized(),
        extraButtons = extraButtons.distinctBy { it.id }.filter { it.id.isNotBlank() }.take(MAX_EXTRA_BUTTONS).map { it.sanitized() },
    )

    fun stick(s: Stick) = if (s == Stick.LEFT) left else right

    companion object {
        const val MAX_NAME = 32
        const val MAX_EXTRA_BUTTONS = 8
    }
}

@Serializable
data class ProfileStore(
    val activeId: String,
    val profiles: List<ControllerProfile>,
    /** Last host that reached STATE_CONNECTED (shown on the dashboard). */
    val lastConnection: LastConnection? = null,
    /** Gyro neutral hold, shared by all profiles (it is about how you hold the phone). */
    val gyroNeutral: GyroNeutral? = null,
    /** PC Mode data: completely separate from the emulator profiles above. */
    val pc: com.ps2gamepad.pc.PcStore = com.ps2gamepad.pc.PcStore(),
    /** PC input (keyboard + mouse HID) enabled: registers the composite descriptor (needs one re-pair). */
    val pcInputEnabled: Boolean = false,
    /** Last mode chosen on the mode selection screen. */
    val lastMode: GamingMode? = null,
    /** Controller theme for both controllers: false = light soft UI (default), true = black. */
    val darkControls: Boolean = false,
    /**
     * Battery saver while a controller is shown (default ON): 60 Hz screen instead of 90/120 Hz,
     * dimmed screen (you look at the PC, not the phone) and 125 Hz Bluetooth reports.
     */
    val batterySaver: Boolean = true,
) {
    val active: ControllerProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

@Serializable
enum class GamingMode(val label: String) { EMULATOR("Emulator Gaming"), PC("PC Gaming") }

@Serializable
data class LastConnection(val hostName: String, val epochMillis: Long, val address: String? = null)

private fun Float.finiteOr(d: Float) = if (isFinite()) this else d
