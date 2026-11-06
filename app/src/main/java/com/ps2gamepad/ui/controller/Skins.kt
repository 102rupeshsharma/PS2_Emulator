package com.ps2gamepad.ui.controller

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** How the four face buttons are marked (the controller uses PlayStation symbols). */
enum class FaceMarks { PS_SYMBOLS, LETTERS }

/**
 * Colours and labels of one controller style. Index order of the face lists is always
 * top, right, bottom, left (= Triangle, Circle, Cross, Square positions = HID buttons 4, 3, 2, 1).
 */
data class SkinTheme(
    val screen: Color,
    val bodyTop: Color,
    val bodyBottom: Color,
    val bodyEdge: Color,
    val centerPanel: Color,
    val key: Color,
    val keyPressed: Color,
    val keyEdge: Color,
    val label: Color,
    val labelPressed: Color,
    val faceMarks: FaceMarks,
    val faceColors: List<Color>,
    val faceLetters: List<String> = emptyList(),
    /** Filled coloured face buttons (Retro) instead of coloured marks on neutral keys. */
    val faceFilled: Boolean = false,
    val stickBase: Color,
    val stickKnobLight: Color,
    val stickKnobDark: Color,
    val accent: Color,
    /** Draw a coloured glow outline around keys (Neon). */
    val glow: Boolean = false,
    val labelL2: String = "L2", val labelL1: String = "L1", val labelR2: String = "R2", val labelR1: String = "R1",
    val labelSelect: String = "SELECT", val labelStart: String = "START",
    val labelL3: String = "L3", val labelR3: String = "R3",
)

object Skins {
    /** Skin colours derived from a soft-UI theme: keys share the background colour (1.8.0). */
    fun of(t: NeuTheme) = SkinTheme(
        screen = t.bg, bodyTop = t.bg, bodyBottom = t.bg, bodyEdge = t.bg,
        centerPanel = t.bg, key = t.bg, keyPressed = t.pressed, keyEdge = t.bg,
        label = t.label, labelPressed = t.label, faceMarks = FaceMarks.PS_SYMBOLS,
        faceColors = List(4) { t.label }, faceFilled = true,
        stickBase = t.pressed, stickKnobLight = t.bg, stickKnobDark = t.bg, accent = t.accent,
    )

    val PS2 = of(NeuThemes.LIGHT)
}

val LocalSkin = staticCompositionLocalOf { Skins.PS2 }
