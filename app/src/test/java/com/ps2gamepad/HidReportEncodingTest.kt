package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidDescriptor
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.Dpad
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.Hat
import com.ps2gamepad.model.Stick
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportEncodingTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun neutralReport() {
        // no buttons, hat = 8 (null) in the high nibble of byte 1, all axes 0
        assertArrayEquals(bytes(0x00, 0x80, 0, 0, 0, 0), HidReportManager.NEUTRAL_REPORT)
        assertEquals(HidDescriptor.REPORT_LENGTH, HidReportManager.NEUTRAL_REPORT.size)
    }

    @Test fun everyButtonHasItsOwnBit() {
        for (b in ControllerButton.entries) {
            val r = HidReportManager.encode(GamepadState.NEUTRAL.withButton(b, true))
            val bit = b.hidButton - 1
            val expected0 = if (bit < 8) 1 shl bit else 0
            val expected1 = (if (bit >= 8) 1 shl (bit - 8) else 0) or 0x80
            assertEquals("${b.label} byte0", expected0, r[0].toInt() and 0xFF)
            assertEquals("${b.label} byte1", expected1, r[1].toInt() and 0xFF)
            val d = HidReportManager.decode(r)
            assertTrue(d.isPressed(b))
            assertEquals(1, Integer.bitCount(d.buttons))
        }
    }

    @Test fun expectedHidButtonNumbers() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), ControllerButton.entries.map { it.hidButton })
        assertEquals(2, ControllerButton.CROSS.hidButton)
        assertEquals(10, ControllerButton.START.hidButton)
    }

    @Test fun allButtonsTogether() {
        var s = GamepadState.NEUTRAL
        ControllerButton.entries.forEach { s = s.withButton(it, true) }
        val r = HidReportManager.encode(s)
        assertEquals(0xFF, r[0].toInt() and 0xFF)
        assertEquals(0x8F, r[1].toInt() and 0xFF) // buttons 9-12 + null hat
    }

    @Test fun releaseClearsOnlyThatButton() {
        val s = GamepadState.NEUTRAL.withButton(ControllerButton.CROSS, true).withButton(ControllerButton.L1, true)
        val r = s.withButton(ControllerButton.CROSS, false)
        assertTrue(r.isPressed(ControllerButton.L1))
        assertFalse(r.isPressed(ControllerButton.CROSS))
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, HidReportManager.encode(r.withButton(ControllerButton.L1, false)))
    }

    @Test fun hatValuesInHighNibble() {
        val cases = mapOf(
            Dpad(up = true) to 0, Dpad(up = true, right = true) to 1, Dpad(right = true) to 2,
            Dpad(down = true, right = true) to 3, Dpad(down = true) to 4, Dpad(down = true, left = true) to 5,
            Dpad(left = true) to 6, Dpad(up = true, left = true) to 7, Dpad() to 8,
        )
        for ((dpad, hat) in cases) {
            val r = HidReportManager.encode(GamepadState(dpad = dpad))
            assertEquals("$dpad", hat, (r[1].toInt() and 0xFF) shr 4)
            assertEquals(0, r[1].toInt() and 0x0F)
        }
    }

    @Test fun hatAndButtonsDoNotInterfere() {
        val s = GamepadState(dpad = Dpad(down = true, left = true)).withButton(ControllerButton.R3, true)
        val d = HidReportManager.decode(HidReportManager.encode(s))
        assertEquals(Hat.DOWN_LEFT.value, d.hat)
        assertTrue(d.isPressed(ControllerButton.R3))
        assertEquals(ControllerButton.R3.mask, d.buttons)
    }

    @Test fun axesAreSignedBytesInOrderXYZRz() {
        val s = GamepadState(leftX = -127, leftY = 127, rightX = 1, rightY = -1)
        val r = HidReportManager.encode(s)
        assertArrayEquals(bytes(0x00, 0x80, 0x81, 0x7F, 0x01, 0xFF), r)
        val d = HidReportManager.decode(r)
        assertEquals(listOf(-127, 127, 1, -1), listOf(d.x, d.y, d.z, d.rz))
    }

    @Test fun axesAreClamped() {
        val r = HidReportManager.encode(GamepadState(leftX = 500, leftY = -500, rightX = -128, rightY = 128))
        val d = HidReportManager.decode(r)
        assertEquals(listOf(127, -127, -127, 127), listOf(d.x, d.y, d.z, d.rz))
        assertEquals(127, GamepadState.NEUTRAL.withStick(Stick.LEFT, 999, 0).leftX)
    }

    @Test fun roundTripRandomStates() {
        val rnd = java.util.Random(42)
        repeat(2000) {
            val s = GamepadState(
                buttons = rnd.nextInt(1 shl 12),
                dpad = Dpad(rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean()),
                leftX = rnd.nextInt(255) - 127, leftY = rnd.nextInt(255) - 127,
                rightX = rnd.nextInt(255) - 127, rightY = rnd.nextInt(255) - 127,
            )
            val d = HidReportManager.decode(HidReportManager.encode(s))
            assertEquals(s.buttons, d.buttons)
            assertEquals(s.dpad.hat.value, d.hat)
            assertEquals(listOf(s.leftX, s.leftY, s.rightX, s.rightY), listOf(d.x, d.y, d.z, d.rz))
        }
    }

    @Test fun sameDigitalStateIgnoresAxes() {
        val a = HidReportManager.encode(GamepadState(leftX = 10).withButton(ControllerButton.CROSS, true))
        val b = HidReportManager.encode(GamepadState(leftX = -90, rightY = 5).withButton(ControllerButton.CROSS, true))
        val c = HidReportManager.encode(GamepadState(leftX = 10))
        assertTrue(HidReportManager.sameDigitalState(a, b))
        assertFalse(HidReportManager.sameDigitalState(a, c))
    }

    @Test fun hatTruthTableOppositesCancel() {
        for (mask in 0 until 16) {
            val up = mask and 1 != 0; val down = mask and 2 != 0; val left = mask and 4 != 0; val right = mask and 8 != 0
            val hat = Hat.from(up, down, left, right)
            val v = (if (up && !down) "U" else "") + (if (down && !up) "D" else "")
            val h = (if (left && !right) "L" else "") + (if (right && !left) "R" else "")
            val expected = when (v + h) {
                "U" -> Hat.UP; "UR" -> Hat.UP_RIGHT; "R" -> Hat.RIGHT; "DR" -> Hat.DOWN_RIGHT
                "D" -> Hat.DOWN; "DL" -> Hat.DOWN_LEFT; "L" -> Hat.LEFT; "UL" -> Hat.UP_LEFT
                else -> Hat.NEUTRAL
            }
            assertEquals("mask=$mask", expected, hat)
        }
    }

    @Test fun hexFormatting() {
        assertEquals("00 80 00 00 00 00", HidReportManager.toHex(HidReportManager.NEUTRAL_REPORT))
    }
}
