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
    /** Plain sky-blue buttons (1.7.0): solid fill, no outlines or glow, dark labels for contrast. */
    val PS2 = SkinTheme(
        screen = Color(0xFF0B0C0E), bodyTop = Color(0xFF26282E), bodyBottom = Color(0xFF1D1F24), bodyEdge = Color(0xFF33363E),
        centerPanel = Color(0x3338BDF8), key = Sky.Key, keyPressed = Sky.Pressed, keyEdge = Sky.Key,
        label = Sky.Label, labelPressed = Color.White, faceMarks = FaceMarks.PS_SYMBOLS,
        faceColors = List(4) { Sky.Key }, faceFilled = true,
        stickBase = Color(0x4038BDF8), stickKnobLight = Color(0xFF7DD3FC), stickKnobDark = Sky.Key, accent = Sky.Key,
    )

}

/** The one button colour used by both controllers (emulator and PC). */
object Sky {
    val Key = Color(0xFF38BDF8)
    val Pressed = Color(0xFF0369A1)
    val Label = Color(0xFF0C2D48)
    val SubLabel = Color(0xFF1E5A85)
}

val LocalSkin = staticCompositionLocalOf { Skins.PS2 }
