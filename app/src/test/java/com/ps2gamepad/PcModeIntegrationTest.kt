package com.ps2gamepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.bluetooth.KeyCodes
import com.ps2gamepad.bluetooth.MouseButton
import com.ps2gamepad.bluetooth.PcReports
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.GamingMode
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.motion.MotionSample
import com.ps2gamepad.motion.SensorKind
import com.ps2gamepad.pc.PcDefaults
import com.ps2gamepad.ui.App
import com.ps2gamepad.ui.AppActions
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ViewModel + MOCK HID + MOCK sensors. Proves app logic, not Windows behaviour. */
@OptIn(ExperimentalCoroutinesApi::class)
class PcModeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val hid = FakeHid()
    private val motion = FakeMotionSource()
    private val laptop = HostDevice("11:22", "RUPESH")
    private var now = 0L
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), emptyList(), { now },
            motionSource = motion, pcTickContext = dispatcher)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.connect(pcInput: Boolean) {
        vm.onPermissionsChanged(true); vm.onForeground(); runCurrent()
        if (pcInput) vm.setPcInputEnabled(true)
        vm.register(); runCurrent()
        hid.emit(HidEvent.AppStatusChanged(true)); hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
    }

    private fun control(id: String) = vm.pcStore.active.controls.first { it.id == id }
    private val kbKeys get() = hid.keyboardReports().last().drop(2).map { it.toInt() and 0xFF }.filter { it != 0 }

    @Test fun modeSelectionIsRememberedAndOpensTheRightController() = runTest(dispatcher) {
        vm.openController()
        assertEquals(Screen.MODE_SELECT, vm.screen.value)
        vm.chooseMode(GamingMode.PC)
        assertEquals(Screen.PC_CONTROLLER, vm.screen.value)
        assertEquals(GamingMode.PC, vm.profiles.value.lastMode)
        vm.chooseMode(GamingMode.EMULATOR)
        assertEquals(Screen.LOADOUTS, vm.screen.value)
        assertEquals(GamingMode.EMULATOR, vm.profiles.value.lastMode)
    }

    @Test fun pcInputOffMeansGamepadDescriptorAndNoKeyboardReports() = runTest(dispatcher) {
        connect(pcInput = false)
        assertFalse("emulator-only users keep the original descriptor", hid.lastComposite)
        vm.chooseMode(GamingMode.PC)
        vm.pcPress(control("a"))
        assertTrue(hid.inputReports.isEmpty())
    }

    @Test fun enablingPcInputReRegistersWithCompositeDescriptor() = runTest(dispatcher) {
        connect(pcInput = false)
        val regs = hid.registerCalls
        vm.setPcInputEnabled(true); runCurrent()
        assertTrue(hid.lastComposite)
        assertEquals(regs + 1, hid.registerCalls)
        assertEquals(1, hid.unregisterCalls)
        vm.setPcInputEnabled(false); runCurrent()
        assertFalse(hid.lastComposite)
    }

    @Test fun buttonsSendKeysAndMouseClicksWithTransitionsOnly() = runTest(dispatcher) {
        connect(pcInput = true)
        vm.chooseMode(GamingMode.PC)
        hid.inputReports.clear()
        vm.pcPress(control("a"))                   // Space
        vm.pcPress(control("sprint"))              // Left Shift
        vm.pcPress(control("lmb"))                 // left click
        assertEquals(listOf(KeyCodes.usage("Space")), kbKeys)
        assertEquals(KeyCodes.modifierBit(KeyCodes.L_SHIFT), hid.keyboardReports().last()[0].toInt() and 0xFF)
        assertEquals(MouseButton.LEFT.bit, PcReports.decodeMouse(hid.mouseReports().last()).buttons)
        val n = hid.inputReports.size
        repeat(50) { vm.pcPress(control("a")) }
        assertEquals("holding sends nothing more", n, hid.inputReports.size)
        vm.pcRelease(control("a")); vm.pcRelease(control("sprint")); vm.pcRelease(control("lmb"))
        assertTrue(kbKeys.isEmpty()); assertEquals(0, PcReports.decodeMouse(hid.mouseReports().last()).buttons)
    }

    @Test fun wasdStickAndStickMouse() = runTest(dispatcher) {
        connect(pcInput = true)
        vm.chooseMode(GamingMode.PC); runCurrent()
        vm.pcMoveKeys(control("move"), setOf(KeyCodes.usage("W"), KeyCodes.usage("D")))
        assertEquals(setOf(KeyCodes.usage("W"), KeyCodes.usage("D")), kbKeys.toSet())
        // right stick full right for ~100 ms -> mouse moves right through the ticker
        vm.pcMouseStick(1f, 0f)
        repeat(12) { now += 8; advanceTimeBy(8); runCurrent() }
        val moved = hid.mouseReports().sumOf { PcReports.decodeMouse(it).dx }
        assertTrue("moved $moved", moved in 60..140) // 900 px/s * ~0.1 s
        vm.pcMouseStick(0f, 0f)
        val after = hid.mouseReports().size
        repeat(10) { now += 8; advanceTimeBy(8); runCurrent() }
        assertEquals("centred stick stops the mouse", after, hid.mouseReports().size)
    }

    @Test fun everythingIsReleasedOnDisconnectInterruptionAndModeSwitch() = runTest(dispatcher) {
        connect(pcInput = true)
        vm.chooseMode(GamingMode.PC)
        vm.pcPress(control("a")); vm.pcPress(control("lmb"))
        vm.onInterrupted("notification shade")
        assertTrue(vm.pcInput.state.value.idle); assertTrue(kbKeys.isEmpty())
        vm.onFocusGained()
        vm.pcPress(control("b"))
        vm.chooseMode(GamingMode.EMULATOR)
        assertTrue(vm.pcInput.state.value.idle); assertTrue(kbKeys.isEmpty())
        vm.chooseMode(GamingMode.PC)
        vm.pcPress(control("x"))
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        assertTrue("released on connection loss", vm.pcInput.state.value.idle)
    }

    @Test fun gyroMouseRunsOnlyInPcControllerAndCanBePaused() = runTest(dispatcher) {
        connect(pcInput = true)
        assertFalse(motion.running)
        vm.chooseMode(GamingMode.PC)
        assertTrue("gyro mouse sensors on", motion.running)
        // turn the phone right (landscape, held at 45°): mouse moves right
        var t = 0L
        val up = Vec3d(0f, 0.7071f, 0.7071f)
        repeat(30) {
            t += 10_000_000L
            motion.listener?.invoke(MotionSample(SensorKind.ACCELEROMETER, up.y * 9.81f, -up.x * 9.81f, up.z * 9.81f, t, 1))
            motion.listener?.invoke(MotionSample(SensorKind.GYROSCOPE, -0.5f * up.y, 0.5f * up.x, -0.5f * up.z, t, 1))
        }
        val dx = hid.mouseReports().sumOf { PcReports.decodeMouse(it).dx }
        assertTrue("gyro turned right -> dx=$dx", dx > 50)
        vm.togglePcGyro()
        assertFalse("paused: sensors off, still connected", motion.running)
        assertTrue(vm.hidState.value.isConnected)
        vm.togglePcGyro()
        assertTrue(motion.running)
        vm.navigate(Screen.HOME)
        assertFalse("leaving PC Mode stops the sensors", motion.running)
    }

    @Test fun emulatorModeIsUnchangedByPcMode() = runTest(dispatcher) {
        connect(pcInput = true)
        vm.chooseMode(GamingMode.EMULATOR)
        vm.playLoadout(com.ps2gamepad.settings.DefaultProfiles.STANDARD)
        hid.inputReports.clear()
        vm.input.setButton(ControllerButton.CROSS, true)
        assertTrue(HidReportManager.decode(hid.sent.last()).isPressed(ControllerButton.CROSS))
        assertTrue("no keyboard/mouse reports from Emulator Mode", hid.inputReports.isEmpty())
        assertFalse("PC gyro mouse not running in Emulator Mode", motion.running)
    }

    @Test fun wheelButtonRepeatsWhileHeld() = runTest(dispatcher) {
        connect(pcInput = true)
        vm.chooseMode(GamingMode.PC); runCurrent()
        vm.pcPress(control("wheel_up"))
        repeat(80) { now += 8; advanceTimeBy(8); runCurrent() } // 640 ms
        vm.pcRelease(control("wheel_up"))
        val steps = hid.mouseReports().count { PcReports.decodeMouse(it).wheel == 1 }
        assertTrue("1 + repeats, got $steps", steps in 3..5)
    }

    private data class Vec3d(val x: Float, val y: Float, val z: Float)
}

/** Real Compose UI, MOCK HID. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
class PcModeUiTest {
    @get:Rule val rule = createComposeRule()
    private val hid = FakeHid()
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), emptyList(), { 0L })
        rule.setContent { App(vm, AppActions({}, {}, {}, {})) }
        rule.runOnIdle {
            vm.onPermissionsChanged(true); vm.onForeground(); vm.setPcInputEnabled(true); vm.register()
            hid.emit(HidEvent.AppStatusChanged(true)); hid.emit(HidEvent.ConnectionChanged(HostDevice("1", "PC"), HidStateReducer.STATE_CONNECTED))
        }
    }

    @Test fun modeSelectionToPcControllerShowsAllDefaultControls() {
        rule.runOnIdle { vm.openController() }
        rule.onNodeWithText("Choose gaming mode").assertIsDisplayed()
        rule.onNodeWithTag("mode_PC").performClick()
        rule.waitForIdle()
        for (c in PcDefaults.controls()) rule.onNodeWithTag("pc_${c.id}").assertExists()
    }

    @Test fun touchingAButtonSendsItsKey() {
        rule.runOnIdle { vm.chooseMode(GamingMode.PC) }
        rule.onNodeWithTag("pc_a").performTouchInput { down(center) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf(KeyCodes.usage("Space")), vm.pcInput.state.value.keys) }
        rule.onNodeWithTag("pc_a").performTouchInput { up() }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(vm.pcInput.state.value.idle) }
    }

    @Test fun pcInputOffShowsTheEnableBanner() {
        rule.runOnIdle { vm.setPcInputEnabled(false); vm.chooseMode(GamingMode.PC) }
        rule.onNodeWithText("PC input is off").assertIsDisplayed()
        rule.onNodeWithTag("pc_enableInput").performClick()
        rule.runOnIdle { assertTrue(vm.profiles.value.pcInputEnabled) }
    }

    @Test fun editModeAddsRenamesRemapsAndDeletesACustomButton() {
        rule.runOnIdle { vm.chooseMode(GamingMode.PC) }
        rule.onNodeWithTag("pc_edit").performClick()
        rule.onNodeWithTag("pc_add").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pc_name").performTextReplacement("Map")
        rule.onNodeWithTag("pc_key_M").performScrollTo().performClick()
        rule.onNodeWithTag("pc_mod_Ctrl").performScrollTo().performClick()
        rule.waitForIdle()
        val custom = vm.pcStore.active.controls.last()
        assertEquals("Map", custom.label); assertEquals("Ctrl+M", custom.action.label); assertTrue(custom.custom)
        rule.onNodeWithTag("pc_delete").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(PcDefaults.controls().size, vm.pcStore.active.controls.size)
        rule.onNodeWithTag("pc_done").performClick()
        rule.runOnIdle { assertFalse(vm.pcEditMode.value) }
    }

    @Test fun editModeSendsNoInput() {
        rule.runOnIdle { vm.chooseMode(GamingMode.PC); vm.setPcEditMode(true) }
        rule.waitForIdle()
        val before = hid.inputReports.size
        rule.onNodeWithTag("pcedit_a").performTouchInput { down(center); up() }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(vm.pcInput.state.value.idle); assertEquals(before, hid.inputReports.size) }
    }

    @Test fun profilesScreenCreateRenameDeleteWithConfirmation() {
        rule.runOnIdle { vm.navigate(Screen.PC_PROFILES) }
        rule.onNodeWithTag("pc_newName").performScrollTo().performTextInput("Elden Ring")
        rule.onNodeWithText("Create").performScrollTo().performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("Elden Ring", vm.pcStore.active.name) }
        val id = vm.pcStore.activeId
        rule.onNodeWithText("Elden Ring  (in use)").assertIsDisplayed()
        rule.runOnIdle { vm.updatePc { com.ps2gamepad.pc.PcProfileManager.select(it, PcDefaults.GENERIC) } }
        rule.waitForIdle()
        rule.onNodeWithTag("pcprofile_$id").performScrollTo()
        rule.runOnIdle { assertTrue(vm.pcStore.profiles.any { it.id == id }) }
    }
}
