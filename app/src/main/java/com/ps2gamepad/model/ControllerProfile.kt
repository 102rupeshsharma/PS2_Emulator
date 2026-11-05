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
    DPAD("D-pad"), FACE("Face buttons"),
    LEFT_STICK("Left stick"), RIGHT_STICK("Right stick"),
    L3("L3"), R3("R3"), SELECT("Select"), START("Start"),
}

/**
 * Visual style of the controller. Purely cosmetic: colours, body and button LABELS change, but every
 * position always sends the same HID button (e.g. the bottom face button is HID button 2 in every
 * style, shown as Cross, A or B). So emulator bindings stay valid when the style changes.
 */
@Serializable
enum class ControllerSkin(val label: String, val tagline: String) {
    PS2_CLASSIC("PS2 Classic", "The original DualShock 2 look"),
    PS5_STYLE("PS5 Style", "Two-tone white, modern"),
    XBOX_STYLE("Xbox Style", "Y B A X, LB/RB, dark green"),
    RETRO("Retro", "Colourful 90s console"),
    NEON("Neon", "Glowing outlines on black"),
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
    val skin: ControllerSkin = ControllerSkin.PS2_CLASSIC,
    /** Extra opacity per control (1 = fully visible), applied on top of [buttonOpacity]. */
    val controlOpacity: Map<ControlId, Float> = emptyMap(),
) {
    fun opacityOf(id: ControlId): Float = controlOpacity[id] ?: 1f

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
) {
    fun sanitized() = copy(
        name = name.trim().take(MAX_NAME).ifEmpty { "Profile" },
        appearance = appearance.sanitized(),
        left = left.sanitized(),
        right = right.sanitized(),
        placements = placements.mapValues { it.value.sanitized() },
        gyro = gyro.sanitized(),
    )

    fun stick(s: Stick) = if (s == Stick.LEFT) left else right

    companion object { const val MAX_NAME = 32 }
}

@Serializable
data class ProfileStore(
    val activeId: String,
    val profiles: List<ControllerProfile>,
    /** Last host that reached STATE_CONNECTED (shown on the dashboard). */
    val lastConnection: LastConnection? = null,
    /** Gyro neutral hold, shared by all profiles (it is about how you hold the phone). */
    val gyroNeutral: GyroNeutral? = null,
) {
    val active: ControllerProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

@Serializable
data class LastConnection(val hostName: String, val epochMillis: Long, val address: String? = null)

private fun Float.finiteOr(d: Float) = if (isFinite()) this else d
