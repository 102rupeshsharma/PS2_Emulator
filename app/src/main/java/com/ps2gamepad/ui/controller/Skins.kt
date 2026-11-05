package com.ps2gamepad.ui.controller

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ps2gamepad.model.ControllerSkin

/** How the four face buttons are marked. */
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

    val PS5 = SkinTheme(
        screen = Color(0xFF0E1015), bodyTop = Color(0xFFF4F5F8), bodyBottom = Color(0xFFDADDE4), bodyEdge = Color(0xFFB8BCC6),
        centerPanel = Color(0xFF1B1D24), key = Color(0xFF22252D), keyPressed = Color(0xFF3B6FF5), keyEdge = Color(0xFF0D0F14),
        label = Color(0xFFE7E9EE), labelPressed = Color.White, faceMarks = FaceMarks.PS_SYMBOLS,
        faceColors = listOf(Color(0xFFD9DCE3), Color(0xFFD9DCE3), Color(0xFFD9DCE3), Color(0xFFD9DCE3)),
        stickBase = Color(0xFF15171C), stickKnobLight = Color(0xFF3A3E48), stickKnobDark = Color(0xFF1F2228), accent = Color(0xFF3B6FF5),
        labelSelect = "CREATE", labelStart = "OPTIONS",
    )

    val XBOX = SkinTheme(
        screen = Color(0xFF070A08), bodyTop = Color(0xFF2B2E2C), bodyBottom = Color(0xFF181A19), bodyEdge = Color(0xFF3A3F3C),
        centerPanel = Color(0xFF202322), key = Color(0xFF2A2D2B), keyPressed = Color(0xFF107C10), keyEdge = Color(0xFF0E100F),
        label = Color(0xFFC9CEC9), labelPressed = Color.White, faceMarks = FaceMarks.LETTERS,
        faceColors = listOf(Color(0xFFFFC83D), Color(0xFFE74C3C), Color(0xFF5DC21E), Color(0xFF2D8CFF)),
        faceLetters = listOf("Y", "B", "A", "X"),
        stickBase = Color(0xFF111312), stickKnobLight = Color(0xFF454A47), stickKnobDark = Color(0xFF262927), accent = Color(0xFF5DC21E),
        labelL2 = "LT", labelL1 = "LB", labelR2 = "RT", labelR1 = "RB", labelSelect = "VIEW", labelStart = "MENU", labelL3 = "LS", labelR3 = "RS",
    )

    val RETRO = SkinTheme(
        screen = Color(0xFF15131C), bodyTop = Color(0xFFD9D6DE), bodyBottom = Color(0xFFBDB8C6), bodyEdge = Color(0xFF8E889B),
        centerPanel = Color(0xFFA8A2B3), key = Color(0xFF55506A), keyPressed = Color(0xFF2E2A3D), keyEdge = Color(0xFF3B3650),
        label = Color(0xFFF1EEF6), labelPressed = Color.White, faceMarks = FaceMarks.LETTERS,
        faceColors = listOf(Color(0xFF3466D6), Color(0xFFD7263D), Color(0xFFF2B705), Color(0xFF1E9E5A)),
        faceLetters = listOf("X", "A", "B", "Y"), faceFilled = true,
        stickBase = Color(0xFF6E6884), stickKnobLight = Color(0xFF8C86A3), stickKnobDark = Color(0xFF4D4864), accent = Color(0xFFD7263D),
        labelL2 = "ZL", labelL1 = "L", labelR2 = "ZR", labelR1 = "R",
    )

    val NEON = SkinTheme(
        screen = Color(0xFF020306), bodyTop = Color(0xFF0B0D16), bodyBottom = Color(0xFF05060B), bodyEdge = Color(0xFF00E5FF),
        centerPanel = Color(0xFF0A0C14), key = Color(0xFF0A0C14), keyPressed = Color(0xFF1D1036), keyEdge = Color(0xFF00E5FF),
        label = Color(0xFF7DF9FF), labelPressed = Color(0xFFFF4FD8), faceMarks = FaceMarks.PS_SYMBOLS,
        faceColors = listOf(Color(0xFF39FF14), Color(0xFFFF3864), Color(0xFF00E5FF), Color(0xFFFF4FD8)),
        stickBase = Color(0xFF07080F), stickKnobLight = Color(0xFF1C2033), stickKnobDark = Color(0xFF0A0C14), accent = Color(0xFFFF4FD8),
        glow = true,
    )

    fun of(s: ControllerSkin): SkinTheme = when (s) {
        ControllerSkin.PS2_CLASSIC -> PS2
        ControllerSkin.PS5_STYLE -> PS5
        ControllerSkin.XBOX_STYLE -> XBOX
        ControllerSkin.RETRO -> RETRO
        ControllerSkin.NEON -> NEON
    }
}

val LocalSkin = staticCompositionLocalOf { Skins.PS2 }
