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
    val PS2 = SkinTheme(
        screen = Color(0xFF0B0C0E), bodyTop = Color(0xFF26282E), bodyBottom = Color(0xFF1D1F24), bodyEdge = Color(0xFF33363E),
        centerPanel = Color(0xFF25272D), key = Color(0xFF2C2F36), keyPressed = Color(0xFF4A4F5A), keyEdge = Color(0xFF14151A),
        label = Color(0xFFB9BCC4), labelPressed = Color.White, faceMarks = FaceMarks.PS_SYMBOLS,
        faceColors = listOf(Color(0xFF3FD18B), Color(0xFFFF5A5F), Color(0xFF7FB2FF), Color(0xFFF08CD0)),
        stickBase = Color(0xFF15161A), stickKnobLight = Color(0xFF41444D), stickKnobDark = Color(0xFF2E3038), accent = Color(0xFFFF3B30),
    )

}

val LocalSkin = staticCompositionLocalOf { Skins.PS2 }
