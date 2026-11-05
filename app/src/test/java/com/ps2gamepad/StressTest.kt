package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.StickMath
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.Dpad
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.Stick
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.settings.DefaultProfiles
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Stress tests with MOCK Bluetooth. They check that state stays consistent and always ends neutral. */
@OptIn(ExperimentalCoroutinesApi::class)
class StressTest {
    private val dispatcher = StandardTestDispatcher()
    private val hid = FakeHid()
    private val bt = MutableStateFlow(BluetoothState.ENABLED)
    private val laptop = HostDevice("11:22:33:44:55:66", "LAPTOP")
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = MainViewModel(hid, bt, { listOf(laptop) }, DiagnosticLog(), emptyList(), { 0L })
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.connect() {
        vm.onPermissionsChanged(true); vm.onForeground(); vm.register(); runCurrent()
        hid.emit(HidEvent.AppStatusChanged(true)); runCurrent()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
    }

    private fun lastIsNeutral() = assertArrayEquals(HidReportManager.NEUTRAL_REPORT, hid.sent.last())

    @Test fun rapidTapping10kTimesOnEveryButton() = runTest(dispatcher) {
        connect()
        val before = hid.sent.size
        repeat(10_000) { i ->
            val b = ControllerButton.entries[i % ControllerButton.entries.size]
            vm.input.setButton(b, true); vm.input.setButton(b, false)
        }
        assertEquals("every press and every release reached the sink", 20_000, hid.sent.size - before)
        lastIsNeutral()
    }

    @Test fun longHoldsWhileEverythingElseChanges() = runTest(dispatcher) {
        connect()
        vm.input.setButton(ControllerButton.R2, true) // accelerator held for the whole "race"
        repeat(50_000) { i ->
            val a = i * 0.05
            vm.input.setStick(Stick.LEFT, StickVector(Math.sin(a).toFloat(), 0f))
            if (i % 100 == 0) vm.input.setButton(ControllerButton.CROSS, i % 200 == 0)
        }
        assertTrue(HidReportManager.decode(hid.sent.last()).isPressed(ControllerButton.R2))
        assertTrue("R2 never dropped", hid.sent.drop(1).all { HidReportManager.decode(it).isPressed(ControllerButton.R2) })
        vm.input.releaseAll()
        lastIsNeutral()
    }

    @Test fun continuousAnalogMovementStaysInRange() = runTest(dispatcher) {
        connect()
        val s = StickSettings(deadZone = 0.12f, curve = 1.6f, sensitivity = 1.4f, maxRange = 0.85f, invertY = true)
        repeat(100_000) { i ->
            val a = i * 0.013
            val r = 50 + 60 * Math.sin(i * 0.0007)
            vm.input.setStick(Stick.LEFT, StickMath.process((Math.cos(a) * r).toFloat(), (Math.sin(a) * r).toFloat(), 100f, s))
            vm.input.setStick(Stick.RIGHT, StickMath.process((Math.sin(a) * r).toFloat(), (Math.cos(a) * r).toFloat(), 100f, s))
        }
        assertTrue(hid.sent.all { r -> HidReportManager.decode(r).let { it.x in -127..127 && it.y in -127..127 && it.z in -127..127 && it.rz in -127..127 } })
        vm.input.setStick(Stick.LEFT, StickVector.ZERO); vm.input.setStick(Stick.RIGHT, StickVector.ZERO)
        lastIsNeutral()
    }

    @Test fun repeatedProfileSwitching() = runTest(dispatcher) {
        connect()
        val ids = DefaultProfiles.all.map { it.id }
        repeat(1_000) { i ->
            vm.input.setButton(ControllerButton.L1, true)
            vm.switchProfile(ids[i % ids.size])
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        }
        runCurrent()
        assertEquals(ids[999 % ids.size], vm.activeProfile.value.id)
        lastIsNeutral()
    }

    @Test fun repeatedConnectionChangesWithHeldInput() = runTest(dispatcher) {
        connect()
        repeat(500) {
            vm.input.setButton(ControllerButton.TRIANGLE, true)
            vm.input.setDpad(Dpad(up = true))
            hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
            hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
            lastIsNeutral() // first thing the host sees after every reconnect
        }
        advanceUntilIdle()
    }

    @Test fun repeatedBackgroundForegroundTransitions() = runTest(dispatcher) {
        connect()
        vm.navigate(Screen.CONTROLLER)
        repeat(200) {
            vm.input.setButton(ControllerButton.SQUARE, true)
            vm.input.setStick(Stick.RIGHT, StickVector(1f, 1f))
            vm.onInterrupted("pause"); vm.onBackground()
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
            lastIsNeutral()
            vm.onForeground(); runCurrent()
        }
    }

    @Test fun bluetoothToggleStorm() = runTest(dispatcher) {
        connect()
        repeat(200) {
            vm.input.setButton(ControllerButton.START, true)
            bt.value = BluetoothState.TURNING_OFF; runCurrent()
            bt.value = BluetoothState.DISABLED; runCurrent()
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
            bt.value = BluetoothState.ENABLED; runCurrent()
        }
        // auto re-registration is bounded, it never loops forever
        assertTrue(hid.registerCalls <= 1 + 200)
    }

    /** Touch thread + "Bluetooth callback" thread hammering the manager at the same time. */
    @Test fun concurrentInputAndResetsNeverCorruptState() {
        val sink = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
        val m = ControllerInputManager({ r, _ -> sink.add(r); true })
        val pool = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        pool.submit { start.await(); repeat(50_000) { i -> m.setButton(ControllerButton.entries[i % 12], i % 2 == 0) } }
        pool.submit { start.await(); repeat(50_000) { i -> m.setStick(Stick.LEFT, StickVector((i % 200 - 100) / 100f, 0f)) } }
        pool.submit { start.await(); repeat(2_000) { m.releaseAll() } }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        m.releaseAll()
        assertEquals(GamepadState.NEUTRAL, m.state.value)
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, sink.last())
        assertTrue(sink.all { it.size == 6 })
    }
}
