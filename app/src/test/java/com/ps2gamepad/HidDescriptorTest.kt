package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the report descriptor with a small HID short-item parser and checks its structure:
 * well-formed items, balanced collections, the gamepad usage, and that the input fields add up
 * to exactly the report length the encoder produces.
 */
class HidDescriptorTest {

    data class Item(val type: Int, val tag: Int, val data: Int, val size: Int)

    private fun parse(d: ByteArray): List<Item> {
        val out = mutableListOf<Item>()
        var i = 0
        while (i < d.size) {
            val prefix = d[i].toInt() and 0xFF
            require(prefix != 0xFE) { "long items are not used" }
            val size = when (prefix and 0x03) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 4 }
            require(i + 1 + size <= d.size) { "truncated item at $i" }
            var data = 0
            for (k in 0 until size) data = data or ((d[i + 1 + k].toInt() and 0xFF) shl (8 * k))
            // sign-extend for logical min/max
            out += Item(type = (prefix shr 2) and 0x03, tag = (prefix shr 4) and 0x0F, data = data, size = size)
            i += 1 + size
        }
        assertEquals("descriptor must end exactly on an item boundary", d.size, i)
        return out
    }

    private val items = parse(HidDescriptor.REPORT_DESCRIPTOR)

    private fun signed(item: Item) = when (item.size) { 1 -> item.data.toByte().toInt(); 2 -> item.data.toShort().toInt(); else -> item.data }

    @Test fun startsWithGamepadApplicationCollection() {
        assertEquals(Item(1, 0x0, 0x01, 1), items[0]) // Usage Page (Generic Desktop)
        assertEquals(Item(2, 0x0, 0x05, 1), items[1]) // Usage (Game Pad)
        assertEquals(Item(0, 0xA, 0x01, 1), items[2]) // Collection (Application)
    }

    @Test fun collectionsAreBalanced() {
        var depth = 0
        for (it in items) {
            if (it.type == 0 && it.tag == 0xA) depth++
            if (it.type == 0 && it.tag == 0xC) depth--
            assertTrue(depth >= 0)
        }
        assertEquals(0, depth)
        val last = items.last()
        assertEquals(0, last.type); assertEquals(0xC, last.tag)
    }

    @Test fun declaresReportIdOne() {
        val ids = items.filter { it.type == 1 && it.tag == 0x8 }.map { it.data }
        assertEquals(listOf(HidDescriptor.REPORT_ID), ids)
    }

    @Test fun inputBitsMatchReportLength() {
        var reportSize = 0; var reportCount = 0; var bits = 0
        for (it in items) {
            when {
                it.type == 1 && it.tag == 0x7 -> reportSize = it.data
                it.type == 1 && it.tag == 0x9 -> reportCount = it.data
                it.type == 0 && it.tag == 0x8 -> bits += reportSize * reportCount // Input
                it.type == 0 && it.tag == 0x9 -> error("no output reports expected")
            }
        }
        assertEquals(HidDescriptor.REPORT_LENGTH * 8, bits)
        assertEquals("byte aligned", 0, bits % 8)
    }

    @Test fun declaresTwelveButtonsHatAndFourAxes() {
        val usages = items.filter { it.type == 2 && it.tag == 0x0 }.map { it.data }
        assertTrue(0x39 in usages) // hat switch
        assertTrue(listOf(0x30, 0x31, 0x32, 0x35).all { it in usages }) // X Y Z Rz
        val usageMax = items.first { it.type == 2 && it.tag == 0x2 }.data
        assertEquals(HidDescriptor.BUTTON_COUNT, usageMax)
    }

    @Test fun axisLogicalRangeIsSymmetric() {
        // last logical min/max pair in the descriptor applies to the axes
        val min = items.last { it.type == 1 && it.tag == 0x1 }
        val max = items.last { it.type == 1 && it.tag == 0x2 }
        assertEquals(-127, signed(min))
        assertEquals(127, signed(max))
    }

    @Test fun hatHasNullState() {
        // The hat's Input item is the second Input; flag bit 6 = Null State.
        val inputs = items.filter { it.type == 0 && it.tag == 0x8 }
        assertEquals(3, inputs.size)
        assertTrue(inputs[1].data and 0x40 != 0)
        assertTrue(HidDescriptor.HAT_NULL > 7)
    }
}
