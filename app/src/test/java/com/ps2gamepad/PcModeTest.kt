package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidDescriptor
import com.ps2gamepad.bluetooth.KeyCodes
import com.ps2gamepad.bluetooth.MouseButton
import com.ps2gamepad.bluetooth.PcReports
import com.ps2gamepad.bluetooth.PendingReport
import com.ps2gamepad.bluetooth.ReportQueue
import com.ps2gamepad.motion.MotionSample
import com.ps2gamepad.motion.SensorKind
import com.ps2gamepad.motion.Vec3
import com.ps2gamepad.pc.GyroMouseEngine
import com.ps2gamepad.pc.PcAction
import com.ps2gamepad.pc.PcActionType
import com.ps2gamepad.pc.PcControlKind
import com.ps2gamepad.pc.PcDefaults
import com.ps2gamepad.pc.PcInputEngine
import com.ps2gamepad.pc.PcMouseSettings
import com.ps2gamepad.pc.PcProfileManager
import com.ps2gamepad.pc.PcReportSink
import com.ps2gamepad.pc.PcStore
import com.ps2gamepad.settings.DefaultProfiles
import com.ps2gamepad.settings.ProfileCodec
import com.ps2gamepad.settings.ProfileManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private val W = KeyCodes.usage("W"); private val A = KeyCodes.usage("A"); private val S = KeyCodes.usage("S"); private val D = KeyCodes.usage("D")
private val E = KeyCodes.usage("E"); private val SPACE = KeyCodes.usage("Space")

class PcDescriptorTest {
    private data class Item(val type: Int, val tag: Int, val data: Int, val size: Int)

    private fun parse(d: ByteArray): List<Item> {
        val out = mutableListOf<Item>(); var i = 0
        while (i < d.size) {
            val p = d[i].toInt() and 0xFF
            val size = when (p and 3) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 4 }
            require(i + 1 + size <= d.size)
            var data = 0
            for (k in 0 until size) data = data or ((d[i + 1 + k].toInt() and 0xFF) shl (8 * k))
            out += Item((p shr 2) and 3, (p shr 4) and 0xF, data, size); i += 1 + size
        }
        return out
    }

    @Test fun gamepadPartIsByteForByteUnchanged() {
        val c = HidDescriptor.COMPOSITE_DESCRIPTOR
        assertArrayEquals(HidDescriptor.REPORT_DESCRIPTOR, c.copyOfRange(0, HidDescriptor.REPORT_DESCRIPTOR.size))
    }

    @Test fun threeApplicationCollectionsWithCorrectReportSizes() {
        val items = parse(HidDescriptor.COMPOSITE_DESCRIPTOR)
        var depth = 0; var apps = 0; var id = 0; var size = 0; var count = 0
        val bits = HashMap<Int, Int>()
        for (it in items) {
            when {
                it.type == 0 && it.tag == 0xA -> { if (depth == 0 && it.data == 1) apps++; depth++ }
                it.type == 0 && it.tag == 0xC -> depth--
                it.type == 1 && it.tag == 0x8 -> id = it.data
                it.type == 1 && it.tag == 0x7 -> size = it.data
                it.type == 1 && it.tag == 0x9 -> count = it.data
                it.type == 0 && it.tag == 0x8 -> bits[id] = (bits[id] ?: 0) + size * count
            }
        }
        assertEquals(0, depth); assertEquals(3, apps)
        assertEquals(HidDescriptor.REPORT_LENGTH * 8, bits[1])
        assertEquals(HidDescriptor.KEYBOARD_REPORT_LENGTH * 8, bits[2])
        assertEquals(HidDescriptor.MOUSE_REPORT_LENGTH * 8, bits[3])
    }

    @Test fun usagesAreKeyboardAndMouse() {
        val items = parse(HidDescriptor.COMPOSITE_DESCRIPTOR)
        val topUsages = items.filter { it.type == 2 && it.tag == 0 }.map { it.data }
        assertTrue(0x05 in topUsages && 0x06 in topUsages && 0x02 in topUsages) // game pad, keyboard, mouse
    }
}

class PcReportsTest {
    @Test fun keyboardLayout() {
        val r = PcReports.keyboard(0x03, listOf(E, SPACE))
        assertArrayEquals(byteArrayOf(0x03, 0, E.toByte(), SPACE.toByte(), 0, 0, 0, 0), r)
        assertEquals(6, PcReports.keyboard(0, (4..20).toList()).drop(2).count { it != 0.toByte() }) // 6-key rollover
    }

    @Test fun mouseRoundTripAndClamp() {
        for ((dx, dy, w) in listOf(Triple(0, 0, 0), Triple(5, -3, 1), Triple(-32767, 32767, -127), Triple(1000, -2000, 0))) {
            val d = PcReports.decodeMouse(PcReports.mouse(MouseButton.RIGHT.bit, dx, dy, w))
            assertEquals(listOf(MouseButton.RIGHT.bit, dx, dy, w), listOf(d.buttons, d.dx, d.dy, d.wheel))
        }
        assertEquals(32767, PcReports.decodeMouse(PcReports.mouse(0, 99999, 0, 0)).dx)
    }

    @Test fun queueSumsMouseMovementAndNeverMergesKeys() {
        val q = ReportQueue()
        repeat(10) { q.offer(PendingReport(PcReports.mouse(0, 3, -2, 0), 0, 3)) }
        assertEquals(1, q.size)
        val m = PcReports.decodeMouse(q.poll()!!.bytes)
        assertEquals(30, m.dx); assertEquals(-20, m.dy)
        q.offer(PendingReport(PcReports.keyboard(0, listOf(E)), 0, 2)); q.offer(PendingReport(PcReports.keyboard(0, emptyList()), 0, 2))
        assertEquals("press and release both kept", 2, q.size)
        q.clear()
        q.offer(PendingReport(PcReports.mouse(1, 5, 0, 0), 0, 3)); q.offer(PendingReport(PcReports.mouse(0, 5, 0, 0), 0, 3))
        assertEquals("button change not merged", 2, q.size)
        q.clear()
        q.offer(PendingReport(com.ps2gamepad.bluetooth.HidReportManager.NEUTRAL_REPORT, 0, 1)); q.offer(PendingReport(PcReports.mouse(0, 5, 0, 0), 0, 3))
        assertEquals("different report ids never merged", 2, q.size)
    }
}

class PcInputEngineTest {
    private class Sink : PcReportSink {
        var connected = true
        val kb = mutableListOf<ByteArray>(); val ms = mutableListOf<ByteArray>()
        override fun keyboard(report: ByteArray, eventUptimeMs: Long) = connected.also { if (it) kb += report }
        override fun mouse(report: ByteArray, eventUptimeMs: Long) = connected.also { if (it) ms += report }
        val keys get() = kb.last().drop(2).map { it.toInt() and 0xFF }.filter { it != 0 }
        val mods get() = kb.last()[0].toInt() and 0xFF
    }
    private val sink = Sink()
    private val e = PcInputEngine(sink) { 0L }

    @Test fun keyDownAndUpOnlyOnTransitions() {
        e.press("a", PcAction.key("Space"))
        assertEquals(listOf(SPACE), sink.keys)
        repeat(100) { e.press("a", PcAction.key("Space")) } // still held: no repeats
        assertEquals(1, sink.kb.size)
        e.release("a")
        assertTrue(sink.keys.isEmpty()); assertEquals(2, sink.kb.size)
        e.release("a")
        assertEquals("releasing twice sends nothing", 2, sink.kb.size)
    }

    @Test fun simultaneousKeysAndSharedKeysAreRefCounted() {
        e.press("sprint", PcAction.key(KeyCodes.L_SHIFT))
        e.press("rt", PcAction.key(KeyCodes.L_SHIFT))
        e.press("jump", PcAction.key("Space"))
        assertEquals(KeyCodes.modifierBit(KeyCodes.L_SHIFT), sink.mods); assertEquals(listOf(SPACE), sink.keys)
        e.release("sprint")
        assertEquals("RT still holds Shift", KeyCodes.modifierBit(KeyCodes.L_SHIFT), sink.mods)
        e.release("rt"); e.release("jump")
        assertEquals(0, sink.mods); assertTrue(sink.keys.isEmpty())
    }

    @Test fun combinationsPressModifierFirstAndReleaseKeyFirst() {
        e.press("c", PcAction.key(E, PcAction.MOD_CTRL))
        val reports = sink.kb.map { (it[0].toInt() and 0xFF) to it[2].toInt() }
        assertEquals(listOf(0x01 to 0, 0x01 to E), reports)
        e.release("c")
        val rel = sink.kb.drop(2).map { (it[0].toInt() and 0xFF) to it[2].toInt() }
        assertEquals(listOf(0x01 to 0, 0 to 0), rel)
    }

    @Test fun stickKeysDiagonalsAndChanges() {
        e.setHeldKeys("move", setOf(W, D))
        assertEquals(setOf(W, D), sink.keys.toSet())
        e.setHeldKeys("move", setOf(D))
        assertEquals(listOf(D), sink.keys)
        e.setHeldKeys("move", emptySet())
        assertTrue(sink.keys.isEmpty())
    }

    @Test fun mouseButtonsAndMovementTogether() {
        e.press("lmb", PcAction.mouse(MouseButton.LEFT))
        assertEquals(MouseButton.LEFT.bit, PcReports.decodeMouse(sink.ms.last()).buttons)
        e.addMotion(2.6f, -1.2f); e.flushMotion()
        val m = PcReports.decodeMouse(sink.ms.last())
        assertEquals(listOf(MouseButton.LEFT.bit, 2, -1), listOf(m.buttons, m.dx, m.dy))
        e.addMotion(0.5f, -0.9f); e.flushMotion() // remainders 0.6+0.5=1.1 , -0.2-0.9=-1.1
        assertEquals(listOf(1, -1), PcReports.decodeMouse(sink.ms.last()).let { listOf(it.dx, it.dy) })
        e.press("w", PcAction.key("W"))
        assertEquals("keyboard and mouse at the same time", listOf(W), sink.keys)
        e.release("lmb")
        assertEquals(0, PcReports.decodeMouse(sink.ms.last()).buttons)
    }

    @Test fun noMovementNoReport() {
        e.addMotion(0.3f, 0.3f); e.flushMotion()
        assertTrue(sink.ms.isEmpty())
    }

    @Test fun releaseAllClearsEverythingAndAlwaysSends() {
        e.press("a", PcAction.key("A")); e.press("lmb", PcAction.mouse(MouseButton.LEFT)); e.setHeldKeys("move", setOf(W, A))
        e.releaseAll()
        assertTrue(sink.keys.isEmpty()); assertEquals(0, sink.mods)
        assertEquals(0, PcReports.decodeMouse(sink.ms.last()).buttons)
        assertTrue(e.state.value.idle)
        val n = sink.kb.size
        e.releaseAll()
        assertEquals("release-all is always sent", n + 1, sink.kb.size)
    }

    @Test fun disconnectedThenReconnectedSendsFullState() {
        e.press("w", PcAction.key("W"))
        sink.connected = false
        e.press("d", PcAction.key("D"))
        sink.connected = true
        e.press("e", PcAction.key("E"))
        assertEquals(setOf(W, D, E), sink.keys.toSet())
    }

    @Test fun wheelSteps() {
        e.wheel(1); e.wheel(-1)
        assertEquals(listOf(1, -1), sink.ms.map { PcReports.decodeMouse(it).wheel })
    }
}

class GyroMouseTest {
    private val dt = 10_000_000L

    /** Feeds [n] samples of constant angular velocity (device frame) with gravity [upDevice]. */
    private fun GyroMouseEngine.run(w: Vec3, upDevice: Vec3, n: Int, rotation: Int = 0, start: Long = 0L): Pair<Float, Float> {
        var x = 0f; var y = 0f; var t = start
        repeat(n) {
            t += dt
            onSample(MotionSample(SensorKind.ACCELEROMETER, upDevice.x * 9.81f, upDevice.y * 9.81f, upDevice.z * 9.81f, t, rotation))
            onSample(MotionSample(SensorKind.GYROSCOPE, w.x, w.y, w.z, t, rotation))?.let { x += it.first; y += it.second }
        }
        return x to y
    }

    private fun engine(s: PcMouseSettings = PcMouseSettings(gyroSmoothing = 0f)) = GyroMouseEngine().also { it.settings = s }

    @Test fun stationaryPhoneDoesNotMoveTheMouse() {
        val (x, y) = engine().run(Vec3(0f, 0f, 0f), Vec3.Z, 500)
        assertEquals(0f, x, 0f); assertEquals(0f, y, 0f)
    }

    @Test fun smallBiasIsSwallowedByTheDeadZone() {
        val b = Math.toRadians(1.0).toFloat() // 1°/s bias < 1.5°/s dead zone
        val (x, y) = engine().run(Vec3(b, -b * 0.5f, b * 0.5f), Vec3.Z, 1000)
        assertEquals(0f, x, 0f); assertEquals(0f, y, 0f)
    }

    @Test fun calibrationRemovesALargeBias() {
        val bias = Vec3(0.06f, -0.05f, 0.04f) // ≈ 3-4 °/s: would drift without calibration
        val e = engine()
        assertTrue(e.run(bias, Vec3.Z, 100).let { abs(it.first) + abs(it.second) } > 1f)
        e.requestCalibration()
        e.run(bias, Vec3.Z, 200, start = 2_000_000_000L)
        val (x, y) = e.run(bias, Vec3.Z, 300, start = 5_000_000_000L)
        assertEquals(0f, x, 0.01f); assertEquals(0f, y, 0.01f)
        assertTrue(e.biasDps > 3f)
    }

    /** Directions for a phone held at 45° in landscape (rotation 1), flat, and upright. */
    @Test fun directionsInDifferentOrientations() {
        val r = 0.5f // rad/s
        for ((rotation, toDevice) in listOf<Pair<Int, (Vec3) -> Vec3>>(1 to { s -> Vec3(s.y, -s.x, s.z) }, 3 to { s -> Vec3(-s.y, s.x, s.z) })) {
            for (upScreen in listOf(Vec3(0f, 0.7071f, 0.7071f), Vec3.Z, Vec3(0f, 0.95f, 0.31f).normalized())) {
                val up = toDevice(upScreen)
                var e1 = Vec3.X - upScreen * (Vec3.X dot upScreen); e1 = e1.normalized()
                val e2 = (upScreen cross e1).normalized()
                fun dev(screen: Vec3) = toDevice(screen)
                // turn right (clockwise seen from above) -> mouse right
                engine().run(dev(upScreen * -r), up, 50, rotation).let { assertTrue("yaw right rot=$rotation up=$upScreen $it", it.first > 50f && abs(it.second) < 1f) }
                // roll right side down -> mouse right
                engine().run(dev(e2 * -r), up, 50, rotation).let { assertTrue("roll right $it", it.first > 50f && abs(it.second) < 1f) }
                // tilt top edge away (forward) -> mouse up (negative y)
                engine().run(dev(e1 * r), up, 50, rotation).let { assertTrue("forward $it", it.second < -50f && abs(it.first) < 1f) }
                // backward -> down
                engine().run(dev(e1 * -r), up, 50, rotation).let { assertTrue("backward $it", it.second > 50f) }
            }
        }
    }

    @Test fun sensitivityInvertAndRateLimit() {
        val turn = Vec3(0f, 0f, -0.5f)
        val base = engine().run(turn, Vec3.Z, 50).first
        assertEquals(base * 2f, engine(PcMouseSettings(gyroSmoothing = 0f, gyroSensitivity = 2f)).run(turn, Vec3.Z, 50).first, base * 0.02f)
        assertEquals(base * 0.5f, engine(PcMouseSettings(gyroSmoothing = 0f, gyroSensitivityX = 0.5f)).run(turn, Vec3.Z, 50).first, base * 0.02f)
        assertEquals(-base, engine(PcMouseSettings(gyroSmoothing = 0f, gyroInvertX = true)).run(turn, Vec3.Z, 50).first, base * 0.02f)
        // a sensor spike of 200 rad/s is capped per sample
        val spike = engine().run(Vec3(0f, 0f, -200f), Vec3.Z, 2).first
        assertTrue(spike <= 2 * GyroMouseEngine.MAX_COUNTS_PER_SAMPLE + 0.1f)
    }
}

class PcProfilesTest {
    private var n = 0
    private val ids = { "p${n++}" }

    @Test fun defaultsFollowTheSpecTable() {
        val c = PcDefaults.controls().associateBy { it.id }
        fun key(id: String) = c.getValue(id).action.key
        assertEquals(KeyCodes.usage("Space"), key("a")); assertEquals(KeyCodes.usage("E"), key("b"))
        assertEquals(KeyCodes.usage("R"), key("x")); assertEquals(KeyCodes.usage("F"), key("y"))
        assertEquals(KeyCodes.usage("Q"), key("lb")); assertEquals(KeyCodes.usage("G"), key("rb"))
        assertEquals(KeyCodes.L_CTRL, key("lt")); assertEquals(KeyCodes.L_SHIFT, key("rt"))
        assertEquals(KeyCodes.usage("Esc"), key("start")); assertEquals(KeyCodes.usage("Tab"), key("select"))
        val move = c.getValue("move")
        assertEquals(PcControlKind.STICK_KEYS, move.kind)
        assertEquals(listOf(W, S, A, D), listOf(move.up, move.down, move.left, move.right))
        assertEquals(MouseButton.LEFT, c.getValue("lmb").action.mouse); assertEquals(MouseButton.RIGHT, c.getValue("rmb").action.mouse)
        assertEquals(PcControlKind.STICK_MOUSE, c.getValue("look").kind)
    }

    @Test fun defaultLayoutHasNoOverlapsOnThePhone() {
        val w = 986f; val h = 443f
        val u = minOf(h, w / 2.4f)
        val rects = PcDefaults.controls().map { c ->
            val d = when (c.kind) { PcControlKind.BUTTON -> u * 0.17f * c.size; else -> u * 0.44f * c.size }
            val bw = if (c.kind == PcControlKind.BUTTON && c.label.length > 4) d * 1.25f else d
            c.id to com.ps2gamepad.controller.ControlRect(c.cx * w, c.cy * h, bw, d)
        }
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse("${rects[i].first} overlaps ${rects[j].first}", rects[i].second.overlaps(rects[j].second))
        }
        for ((id, r) in rects) assertTrue("$id on screen", r.left >= 0 && r.right <= w && r.top >= 0 && r.bottom <= h)
    }

    @Test fun profileCrudAndLimit() {
        var s = PcStore()
        s = PcProfileManager.create(s, "Elden Ring", s.activeId, ids)
        assertEquals("Elden Ring", s.active.name); assertFalse(s.active.builtIn)
        s = PcProfileManager.rename(s, s.activeId, "Elden Ring PC")
        assertEquals("Elden Ring PC", s.active.name)
        assertEquals("duplicate names refused", s, PcProfileManager.rename(s, s.activeId, "Generic PC Game"))
        s = PcProfileManager.duplicate(s, s.activeId, ids)
        assertEquals("Elden Ring PC copy", s.active.name)
        assertEquals("built-in not deletable", s, PcProfileManager.delete(s, PcDefaults.GENERIC))
        val before = s.profiles.size
        s = PcProfileManager.delete(s, s.activeId)
        assertEquals(before - 1, s.profiles.size)
        repeat(20) { s = PcProfileManager.create(s, "G$it", s.activeId, ids) }
        assertEquals(PcDefaults.MAX_PROFILES, s.profiles.size)
    }

    @Test fun customButtonsAddEditDeleteAndLimit() {
        var s = PcStore()
        s = PcProfileManager.addCustomButton(s, "Reload", PcAction.key("R"), ids)
        val id = s.active.controls.last().id
        s = PcProfileManager.updateControl(s, id) { it.copy(label = "Interact", action = PcAction.key(KeyCodes.usage("E"), PcAction.MOD_CTRL), size = 1.4f, opacity = 0.5f, cx = 0.3f) }
        val c = s.active.controls.last()
        assertEquals("Interact", c.label); assertEquals("Ctrl+E", c.action.label); assertEquals(1.4f, c.size); assertEquals(0.5f, c.opacity)
        s = PcProfileManager.deleteControl(s, PcDefaults.controls().first().id)
        assertEquals("default controls cannot be deleted", PcDefaults.controls().size + 1, s.active.controls.size)
        s = PcProfileManager.deleteControl(s, id)
        assertEquals(PcDefaults.controls().size, s.active.controls.size)
        repeat(30) { s = PcProfileManager.addCustomButton(s, "B$it", PcAction.key("K"), ids) }
        assertEquals(PcDefaults.MAX_CUSTOM_BUTTONS, PcProfileManager.customCount(s.active))
    }

    @Test fun restoreDefaultsResetsLayout() {
        var s = PcProfileManager.addCustomButton(PcStore(), "X", PcAction.key("X"), ids)
        s = PcProfileManager.restoreDefaults(s, PcDefaults.GENERIC)
        assertEquals(PcDefaults.controls(), s.active.controls)
    }

    @Test fun pcDataPersistsAndEmulatorDataIsUntouched() {
        val emu = DefaultProfiles.store()
        var pc = PcProfileManager.create(PcStore(), "FIFA", PcDefaults.GENERIC, ids)
        pc = PcProfileManager.addCustomButton(pc, "Shoot", PcAction.mouse(MouseButton.LEFT), ids)
        val s = emu.copy(pc = pc, pcInputEnabled = true, lastMode = com.ps2gamepad.model.GamingMode.PC)
        val back = ProfileCodec.decode(ProfileCodec.encode(s))
        assertEquals(pc, back.pc)
        assertTrue(back.pcInputEnabled); assertEquals(com.ps2gamepad.model.GamingMode.PC, back.lastMode)
        assertEquals("emulator profiles unchanged", emu.profiles, back.profiles)
        assertEquals(emu.activeId, back.activeId)
        // and the emulator profile manager never touches PC data
        val afterEmuEdit = ProfileManager.updateActive(back) { it.copy(name = "x") }
        assertEquals(pc, afterEmuEdit.pc)
    }

    @Test fun oldSettingsWithoutPcSectionLoadDefaults() {
        val old = """{"activeId":"standard","profiles":[{"id":"standard","name":"Standard PS2","builtIn":true}]}"""
        val s = ProfileCodec.decode(old)
        assertEquals(PcStore(), s.pc); assertFalse(s.pcInputEnabled)
    }

    @Test fun actionLabels() {
        assertEquals("Space", PcAction.key("Space").label)
        assertEquals("Ctrl+Shift+E", PcAction.key(KeyCodes.usage("E"), PcAction.MOD_CTRL or PcAction.MOD_SHIFT).label)
        assertEquals("Right click", PcAction.mouse(MouseButton.RIGHT).label)
        assertEquals("Wheel up", PcAction(PcActionType.WHEEL_UP).label)
    }
}
