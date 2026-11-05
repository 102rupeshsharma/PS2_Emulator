package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidController
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.bluetooth.SenderStats
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.model.HidState
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.RegistrationState
import com.ps2gamepad.model.Stick
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val hid = FakeHid()
    private val bt = MutableStateFlow(BluetoothState.ENABLED)
    private val laptop = HostDevice("11:22:33:44:55:66", "LAPTOP")
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = MainViewModel(hid, bt, { listOf(laptop) }, DiagnosticLog(), listOf("test device"), { 0L })
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.connect() {
        vm.onPermissionsChanged(true)
        vm.register(); runCurrent()
        hid.emit(HidEvent.AppStatusChanged(true)); runCurrent()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
    }

    @Test fun registerNeedsPermission() = runTest(dispatcher) {
        runCurrent()
        vm.register(); runCurrent()
        assertEquals(0, hid.registerCalls)
        vm.onPermissionsChanged(true)
        vm.register(); runCurrent()
        assertEquals(1, hid.registerCalls)
    }

    @Test fun registerNeedsBluetoothOn() = runTest(dispatcher) {
        bt.value = BluetoothState.DISABLED; runCurrent()
        vm.onPermissionsChanged(true)
        vm.register(); runCurrent()
        assertEquals(0, hid.registerCalls)
    }

    @Test fun permissionGrantLoadsBondedHosts() = runTest(dispatcher) {
        assertTrue(vm.bondedHosts.value.isEmpty())
        vm.onPermissionsChanged(true)
        assertEquals(listOf(laptop), vm.bondedHosts.value)
    }

    @Test fun connectSendsNeutralBaseline() = runTest(dispatcher) {
        connect()
        assertTrue(vm.hidState.value.isConnected)
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, hid.sent.last())
    }

    @Test fun testButtonsToggleRealReports() = runTest(dispatcher) {
        connect()
        vm.toggleTestButton(1)
        assertTrue(HidReportManager.decode(hid.sent.last()).isPressed(ControllerButton.SQUARE))
        vm.toggleTestButton(2)
        assertEquals(ControllerButton.SQUARE.mask or ControllerButton.CROSS.mask, HidReportManager.decode(hid.sent.last()).buttons)
        vm.toggleTestButton(1)
        assertEquals(ControllerButton.CROSS.mask, HidReportManager.decode(hid.sent.last()).buttons)
        vm.releaseAllInputs()
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, hid.sent.last())
    }

    @Test fun connectionLossResetsInputsAndAutoReconnects() = runTest(dispatcher) {
        connect()
        vm.input.setButton(ControllerButton.CROSS, true)
        vm.input.setStick(Stick.LEFT, StickVector(1f, 0f))
        val epoch = vm.input.resetEpoch.value
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        assertTrue(vm.input.resetEpoch.value > epoch)
        advanceTimeBy(2_001); runCurrent()
        assertEquals(listOf(laptop.address), hid.connects)
        // host reconnects -> attempts stop
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
        advanceUntilIdle()
        assertEquals(1, hid.connects.size)
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, hid.sent.last())
    }

    @Test fun reconnectGivesUpAfterThreeAttempts() = runTest(dispatcher) {
        connect()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        advanceUntilIdle()
        assertEquals(3, hid.connects.size)
    }

    @Test fun userDisconnectDoesNotAutoReconnect() = runTest(dispatcher) {
        connect()
        vm.disconnect()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        advanceUntilIdle()
        assertTrue(hid.connects.isEmpty())
    }

    @Test fun bluetoothOffReleasesAndInformsHidLayer() = runTest(dispatcher) {
        connect()
        vm.input.setButton(ControllerButton.L1, true)
        bt.value = BluetoothState.TURNING_OFF; runCurrent()
        assertEquals(1, hid.bluetoothOffCalls)
        assertFalse(vm.hidState.value.isConnected)
        assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
    }

    @Test fun bluetoothBackOnReRegisters() = runTest(dispatcher) {
        connect()
        bt.value = BluetoothState.DISABLED; runCurrent()
        bt.value = BluetoothState.ENABLED; runCurrent()
        assertEquals(2, hid.registerCalls)
    }

    @Test fun foregroundReRegistersAfterSystemDroppedRegistration() = runTest(dispatcher) {
        connect()
        hid.emit(HidEvent.AppStatusChanged(false)); runCurrent() // system unregistered us in background
        vm.onForeground(); runCurrent()
        assertEquals(2, hid.registerCalls)
    }

    @Test fun droppedWhileVisibleReRegistersImmediately() = runTest(dispatcher) {
        vm.onForeground()
        connect()
        hid.emit(HidEvent.AppStatusChanged(false)); runCurrent()
        assertEquals(2, hid.registerCalls)
    }

    @Test fun droppedInBackgroundWaitsForForeground() = runTest(dispatcher) {
        vm.onForeground()
        connect()
        vm.onBackground()
        hid.emit(HidEvent.AppStatusChanged(false)); runCurrent()
        assertEquals(1, hid.registerCalls)
        vm.onForeground(); runCurrent()
        assertEquals(2, hid.registerCalls)
    }

    @Test fun autoReRegisterLoopIsBounded() = runTest(dispatcher) {
        vm.onForeground()
        vm.onPermissionsChanged(true)
        vm.register(); runCurrent()
        // The stack accepts and immediately drops the registration, over and over.
        repeat(10) {
            hid.emit(HidEvent.AppStatusChanged(true)); runCurrent()
            hid.emit(HidEvent.AppStatusChanged(false)); runCurrent()
        }
        assertEquals(1 + MainViewModel.MAX_AUTO_REGISTER, hid.registerCalls)
    }

    @Test fun noReRegisterAfterUserUnregister() = runTest(dispatcher) {
        connect()
        vm.unregister(); runCurrent()
        vm.onForeground(); runCurrent()
        assertEquals(1, hid.registerCalls)
        assertEquals(1, hid.unregisterCalls)
    }

    @Test fun failedRegistrationIsNotRetriedAutomatically() = runTest(dispatcher) {
        vm.onPermissionsChanged(true)
        vm.register(); runCurrent()
        hid.emit(HidEvent.RegisterRejected("HID Device profile not supported")); runCurrent()
        assertTrue(vm.hidState.value.registration is RegistrationState.Failed)
        vm.onForeground(); runCurrent()
        assertEquals(1, hid.registerCalls)
    }

    @Test fun interruptionReleasesEverything() = runTest(dispatcher) {
        connect()
        vm.input.setButton(ControllerButton.TRIANGLE, true)
        vm.input.setStick(Stick.RIGHT, StickVector(0f, -1f))
        vm.onInterrupted("window lost focus")
        assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, hid.sent.last())
    }

    @Test fun navigationReleasesInputs() = runTest(dispatcher) {
        connect()
        vm.navigate(Screen.CONTROLLER)
        vm.input.setButton(ControllerButton.START, true)
        vm.navigate(Screen.DIAGNOSTICS)
        assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        assertEquals(Screen.DIAGNOSTICS, vm.screen.value)
    }

    @Test fun noReportsWhileNotConnected() = runTest(dispatcher) {
        vm.onPermissionsChanged(true)
        vm.input.setButton(ControllerButton.CROSS, true)
        assertTrue(hid.sent.isEmpty())
    }

    // ---------------------------------------------------------------- Phase 3

    @Test fun startsOnDashboard() = assertEquals(Screen.DASHBOARD, vm.screen.value)

    @Test fun connectionDurationAndLastConnectionAreReal() = runTest(dispatcher) {
        runCurrent()
        assertEquals(null, vm.connectedSince.value)
        connect()
        assertEquals(0L, vm.connectedSince.value) // the injected uptime clock returns 0
        assertEquals("LAPTOP", vm.profiles.value.lastConnection?.hostName)
        assertEquals(laptop.address, vm.profiles.value.lastConnection?.address)
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        assertEquals(null, vm.connectedSince.value)
    }

    @Test fun waitingForHostIsNotAConnection() = runTest(dispatcher) {
        vm.onPermissionsChanged(true)
        vm.register(); runCurrent()
        hid.emit(HidEvent.AppStatusChanged(true)); runCurrent()
        assertEquals(null, vm.connectedSince.value)
        assertEquals(null, vm.profiles.value.lastConnection)
    }

    @Test fun manualReconnectUsesLastHost() = runTest(dispatcher) {
        connect()
        vm.disconnect()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        vm.reconnect()
        assertEquals(listOf(laptop.address), hid.connects)
    }

    @Test fun reconnectWithoutHistoryDoesNothing() = runTest(dispatcher) {
        vm.reconnect()
        assertTrue(hid.connects.isEmpty())
    }

    @Test fun editModeReleasesInputs() = runTest(dispatcher) {
        connect()
        vm.navigate(Screen.CONTROLLER)
        vm.input.setButton(ControllerButton.CROSS, true)
        vm.setEditMode(true)
        assertTrue(vm.editMode.value)
        assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        assertArrayEquals(HidReportManager.NEUTRAL_REPORT, hid.sent.last())
        vm.navigate(Screen.DASHBOARD)
        assertFalse("leaving the controller ends edit mode", vm.editMode.value)
    }

    @Test fun profileSwitchReleasesInputsAndKeepsHidMapping() = runTest(dispatcher) {
        connect()
        vm.input.setButton(ControllerButton.L1, true)
        vm.switchProfile(com.ps2gamepad.settings.DefaultProfiles.NFS_MW); runCurrent()
        assertEquals("NFS profile active", com.ps2gamepad.settings.DefaultProfiles.NFS_MW, vm.activeProfile.value.id)
        assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        vm.input.setButton(ControllerButton.L1, true)
        assertEquals("same HID bit in every profile", ControllerButton.L1.mask, HidReportManager.decode(hid.sent.last()).buttons)
    }

    @Test fun settingsUpdatesApplyImmediately() = runTest(dispatcher) {
        runCurrent()
        vm.updateAppearance { it.copy(buttonOpacity = 0.4f) }
        vm.updateStick(Stick.RIGHT) { it.copy(invertY = true, curve = 2f) }; runCurrent()
        assertEquals(0.4f, vm.activeProfile.value.appearance.buttonOpacity)
        assertTrue(vm.activeProfile.value.right.invertY)
        assertFalse(vm.activeProfile.value.left.invertY)
        vm.calibrateCenter(Stick.LEFT, 0.1f, -0.1f); runCurrent()
        assertEquals(0.1f, vm.activeProfile.value.left.centerOffsetX)
    }

    @Test fun profileCrudThroughViewModel() = runTest(dispatcher) {
        runCurrent()
        vm.createProfile("Test"); runCurrent()
        val id = vm.activeProfile.value.id
        vm.renameProfile(id, "Renamed"); runCurrent()
        assertEquals("Renamed", vm.activeProfile.value.name)
        vm.duplicateProfile(id); runCurrent()
        assertEquals("Renamed copy", vm.activeProfile.value.name)
        vm.deleteProfile(vm.activeProfile.value.id); runCurrent()
        assertEquals(7, vm.profiles.value.profiles.size)
    }

    @Test fun controllerSessionRecordsStart() = runTest(dispatcher) {
        vm.navigate(Screen.CONTROLLER)
        assertEquals(0L, vm.session.value?.startUptimeMs)
    }

    @Test fun userDisconnectDisablesStackAutoConnectOnReRegister() = runTest(dispatcher) {
        vm.onForeground()
        connect()
        assertTrue(hid.lastAutoConnect)
        vm.disconnect()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        vm.onBackground()
        hid.emit(HidEvent.AppStatusChanged(false)); runCurrent() // system dropped us in background
        vm.onForeground(); runCurrent()
        assertEquals(2, hid.registerCalls)
        assertFalse("must not reconnect to the PC the user disconnected from", hid.lastAutoConnect)
    }
}
