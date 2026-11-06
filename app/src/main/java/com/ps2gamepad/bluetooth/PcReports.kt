package com.ps2gamepad.bluetooth

/**
 * USB HID Keyboard/Keypad usages (page 0x07) offered in PC Mode, with display names.
 * Modifier keys (0xE0..0xE7) are sent as modifier bits, not in the key array.
 */
object KeyCodes {
    data class Key(val usage: Int, val name: String)

    const val L_CTRL = 0xE0
    const val L_SHIFT = 0xE1
    const val L_ALT = 0xE2
    const val L_GUI = 0xE3
    const val R_CTRL = 0xE4
    const val R_SHIFT = 0xE5
    const val R_ALT = 0xE6

    val ALL: List<Key> = buildList {
        for (c in 'A'..'Z') add(Key(0x04 + (c - 'A'), c.toString()))
        for (d in 1..9) add(Key(0x1E + d - 1, d.toString()))
        add(Key(0x27, "0"))
        addAll(listOf(
            Key(0x2C, "Space"), Key(0x28, "Enter"), Key(0x29, "Esc"), Key(0x2B, "Tab"), Key(0x2A, "Backspace"),
            Key(L_SHIFT, "L Shift"), Key(L_CTRL, "L Ctrl"), Key(L_ALT, "L Alt"),
            Key(R_SHIFT, "R Shift"), Key(R_CTRL, "R Ctrl"), Key(R_ALT, "R Alt"),
            Key(0x52, "Up"), Key(0x51, "Down"), Key(0x50, "Left"), Key(0x4F, "Right"),
            Key(0x39, "Caps Lock"), Key(0x49, "Insert"), Key(0x4C, "Delete"), Key(0x4A, "Home"), Key(0x4D, "End"),
            Key(0x4B, "Page Up"), Key(0x4E, "Page Down"),
            Key(0x2D, "-"), Key(0x2E, "="), Key(0x2F, "["), Key(0x30, "]"), Key(0x31, "\\"),
            Key(0x33, ";"), Key(0x34, "'"), Key(0x35, "`"), Key(0x36, ","), Key(0x37, "."), Key(0x38, "/"),
        ))
        for (f in 1..12) add(Key(0x3A + f - 1, "F$f"))
    }

    private val byUsage = ALL.associateBy { it.usage }
    fun name(usage: Int): String = byUsage[usage]?.name ?: "0x%02X".format(usage)
    fun usage(name: String): Int = ALL.first { it.name.equals(name, ignoreCase = true) }.usage

    fun isModifier(usage: Int) = usage in 0xE0..0xE7
    /** Modifier usage -> bit in the keyboard report's modifier byte. */
    fun modifierBit(usage: Int): Int = 1 shl (usage - 0xE0)
}

/** Mouse buttons in report bit order. */
enum class MouseButton(val bit: Int, val label: String) {
    LEFT(0x01, "Left click"), RIGHT(0x02, "Right click"), MIDDLE(0x04, "Middle click"), BACK(0x08, "Back"), FORWARD(0x10, "Forward")
}

/** Encoders for the PC Mode reports (see HidDescriptor KEYBOARD/MOUSE collections). */
object PcReports {
    /** [keys] = pressed non-modifier usages in press order; only the first 6 fit (HID 6-key rollover). */
    fun keyboard(modifiers: Int, keys: List<Int>): ByteArray {
        val r = ByteArray(HidDescriptor.KEYBOARD_REPORT_LENGTH)
        r[0] = (modifiers and 0xFF).toByte()
        keys.take(6).forEachIndexed { i, k -> r[2 + i] = k.toByte() }
        return r
    }

    fun mouse(buttons: Int, dx: Int, dy: Int, wheel: Int): ByteArray {
        val x = dx.coerceIn(-MAX_MOVE, MAX_MOVE)
        val y = dy.coerceIn(-MAX_MOVE, MAX_MOVE)
        val w = wheel.coerceIn(-127, 127)
        return byteArrayOf(
            (buttons and 0x1F).toByte(),
            (x and 0xFF).toByte(), ((x shr 8) and 0xFF).toByte(),
            (y and 0xFF).toByte(), ((y shr 8) and 0xFF).toByte(),
            w.toByte(),
        )
    }

    data class DecodedMouse(val buttons: Int, val dx: Int, val dy: Int, val wheel: Int)

    fun decodeMouse(r: ByteArray) = DecodedMouse(
        r[0].toInt() and 0x1F,
        ((r[1].toInt() and 0xFF) or (r[2].toInt() shl 8)).toShort().toInt(),
        ((r[3].toInt() and 0xFF) or (r[4].toInt() shl 8)).toShort().toInt(),
        r[5].toInt(),
    )

    /** Sums two mouse reports with the same buttons (used when queueing, so no movement is lost). */
    fun mergeMouse(a: ByteArray, b: ByteArray): ByteArray? {
        val x = decodeMouse(a); val y = decodeMouse(b)
        if (x.buttons != y.buttons || x.wheel != 0 || y.wheel != 0) return null
        val dx = x.dx + y.dx; val dy = x.dy + y.dy
        if (dx !in -MAX_MOVE..MAX_MOVE || dy !in -MAX_MOVE..MAX_MOVE) return null
        return mouse(x.buttons, dx, dy, 0)
    }

    const val MAX_MOVE = 32767
}
