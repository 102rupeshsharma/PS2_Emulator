package com.ps2gamepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.controller.StickVector
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.Stick
import com.ps2gamepad.motion.MotionSample
import com.ps2gamepad.motion.MotionSource
import com.ps2gamepad.motion.MotionStatus
import com.ps2gamepad.motion.SensorKind
import com.ps2gamepad.motion.Vec3
import com.ps2gamepad.ui.App
import com.ps2gamepad.ui.AppActions
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/** MOCK motion sensors: the test pushes samples synchronously (as if from the sensor thread). */
class FakeMotionSource(override val status: MotionStatus = MotionStatus(true, true)) : MotionSource {
    var listener: ((MotionSample) -> Unit)? = null
    var starts = 0; var stops = 0
    val running get() = listener != null
    private var t = 0L
    override fun start(onSample: (MotionSample) -> Unit): Boolean { if (!status.usable) return false; listener = onSample; starts++; return true }
    override fun stop() { listener = null; stops++ }

    private var lastDevUp: Vec3? = null

    /**
     * Holds the phone with screen-space up vector [up] for [n] samples at 100 Hz (landscape,
     * rotation 1). Moving to a new hold is simulated realistically: 0.1 s of gyroscope rotation
     * (plus matching accelerometer readings), as a real phone reports while it is being tilted.
     */
    fun hold(up: Vec3, n: Int = 100) {
        val d = Vec3(up.y, -up.x, up.z).normalized()
        val prev = lastDevUp
        if (prev != null) {
            val c = prev cross d
            val sn = c.length
            if (sn > 1e-6f) {
                val ang = kotlin.math.atan2(sn, prev dot d)
                val k = c * (1f / sn)
                val w = k * (-ang / 0.1f) // du/dt = -w x u rotates u towards d
                for (i in 1..10) {
                    t += 10_000_000L
                    listener?.invoke(MotionSample(SensorKind.GYROSCOPE, w.x, w.y, w.z, t, 1))
                    val mid = rotate(prev, k, ang * i / 10f)
                    listener?.invoke(MotionSample(SensorKind.ACCELEROMETER, mid.x * 9.81f, mid.y * 9.81f, mid.z * 9.81f, t, 1))
                }
            }
        }
        lastDevUp = d
        repeat(n) {
            t += 10_000_000L
            listener?.invoke(MotionSample(SensorKind.GYROSCOPE, 0f, 0f, 0f, t, 1))
            listener?.invoke(MotionSample(SensorKind.ACCELEROMETER, d.x * 9.81f, d.y * 9.81f, d.z * 9.81f, t, 1))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GyroViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val hid = FakeHid()
    private val motion = FakeMotionSource()
    private val laptop = HostDevice("11:22", "RUPESH")
    private val neutral = Vec3(0f, 0.7071f, 0.7071f)
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), emptyList(), { 0L }, motionSource = motion)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.connectedOnController(gyro: Boolean = true) {
        vm.onPermissionsChanged(true); vm.onForeground(); vm.register(); runCurrent()
        hid.emit(HidEvent.AppStatusChanged(true)); hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
        vm.updateGyro { it.copy(enabled = gyro, smoothing = 0f) }
        vm.navigate(Screen.CONTROLLER)
        motion.hold(neutral, 50)
        vm.gyroRecenter()
        motion.hold(neutral, 20)
        runCurrent()
    }

    private val lastReport get() = HidReportManager.decode(hid.sent.last())

    @Test fun tiltDrivesTheRightStickInEveryQuadrantThroughHid() = runTest(dispatcher) {
        connectedOnController()
        assertTrue(motion.running)
        assertEquals(0, lastReport.z); assertEquals(0, lastReport.rz)
        for ((tx, ty) in listOf(15f to 0f, -15f to 0f, 0f to -15f, 0f to 15f, 12f to -12f, -12f to 12f, 9f to 4f)) {
            motion.hold(tiltedUp(neutral, tx, ty), 50)
            val r = lastReport
            assertEquals("x sign for ($tx,$ty)", Math.signum(tx).toInt(), Integer.signum(r.z))
            assertEquals("y sign for ($tx,$ty)", -Math.signum(ty).toInt(), Integer.signum(r.rz)) // 1.7.0: tilt up = look up
        }
        motion.hold(neutral, 50)
        assertEquals(0, lastReport.z); assertEquals(0, lastReport.rz)
    }

    @Test fun leftStickAndButtonsAreUnaffected() = runTest(dispatcher) {
        connectedOnController()
        vm.input.setStick(Stick.LEFT, StickVector(-1f, 0f))
        vm.input.setButton(ControllerButton.CROSS, true)
        motion.hold(tiltedUp(neutral, 15f, -15f), 50)
        val r = lastReport
        assertEquals(-127, r.x); assertEquals(0, r.y)
        assertTrue(r.isPressed(ControllerButton.CROSS))
        assertTrue(r.z > 0 && r.rz > 0) // top edge away = look down (1.7.0)
    }

    @Test fun gyroOffMeansSensorsOffAndRightStickCentred() = runTest(dispatcher) {
        connectedOnController(gyro = true)
        motion.hold(tiltedUp(neutral, 15f, 0f), 50)
        assertTrue(lastReport.z > 0)
        vm.updateGyro { it.copy(enabled = false) }; runCurrent()
        assertFalse(motion.running)
        assertEquals("centred report sent when switched off", 0, lastReport.z)
    }

    @Test fun focusLossAndBackgroundCentreAndStopSensors() = runTest(dispatcher) {
        connectedOnController()
        motion.hold(tiltedUp(neutral, 15f, 0f), 50)
        vm.onInterrupted("notification shade")
        assertFalse(motion.running)
        assertEquals(0, lastReport.z)
        vm.onFocusGained()
        assertTrue(motion.running)
        vm.onBackground()
        assertFalse(motion.running)
    }

    @Test fun editModeAndOtherScreensStopTheGyro() = runTest(dispatcher) {
        connectedOnController()
        vm.setEditMode(true)
        assertFalse(motion.running)
        vm.setEditMode(false)
        assertTrue(motion.running)
        vm.navigate(Screen.CUSTOMIZE)
        assertFalse(motion.running)
    }

    @Test fun gyroTestScreenRunsSensorsButSendsOnlyWhenEnabled() = runTest(dispatcher) {
        connectedOnController(gyro = false)
        assertFalse(motion.running)
        vm.navigate(Screen.GYRO)
        assertTrue("live preview runs", motion.running)
        val before = hid.sent.size
        motion.hold(tiltedUp(neutral, 15f, 0f), 50)
        assertTrue(vm.gyroReading.value.stick.x > 0.3f)
        assertEquals("nothing sent while gyro is off", before, hid.sent.size)
        vm.updateGyro { it.copy(enabled = true) }
        motion.hold(tiltedUp(neutral, 15f, 0f), 10)
        assertTrue(lastReport.z > 0)
    }

    @Test fun noReportFloodFromSensorJitter() = runTest(dispatcher) {
        connectedOnController()
        motion.hold(tiltedUp(neutral, 10f, 0f), 50)
        val before = hid.sent.size
        // tiny wobble around the same tilt: below the 2-count jitter filter
        repeat(200) { i -> motion.hold(tiltedUp(neutral, 10f + if (i % 2 == 0) 0.05f else -0.05f, 0f), 1) }
        assertTrue("jitter produced ${hid.sent.size - before} reports", hid.sent.size - before <= 2)
    }

    @Test fun heldTiltIsResentAfterAnInputReset() = runTest(dispatcher) {
        connectedOnController()
        motion.hold(tiltedUp(neutral, 15f, 0f), 50)
        val z = lastReport.z
        assertTrue(z > 0)
        // screen change / reconnect / back press reset all inputs to neutral...
        vm.navigate(Screen.GYRO)
        assertEquals(0, lastReport.z)
        // ...the phone is still tilted and not moving: the very next sample restores the stick
        motion.hold(tiltedUp(neutral, 15f, 0f), 1)
        assertEquals(z, lastReport.z)
        // and after a reconnect (which also resets inputs)
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_DISCONNECTED)); runCurrent()
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED)); runCurrent()
        motion.hold(tiltedUp(neutral, 15f, 0f), 1)
        assertEquals(z, lastReport.z)
    }

    @Test fun recenterIsSavedForNextTime() = runTest(dispatcher) {
        connectedOnController()
        motion.hold(tiltedUp(neutral, 20f, 0f), 50)
        vm.gyroRecenter()
        motion.hold(tiltedUp(neutral, 20f, 0f), 5)
        runCurrent()
        val saved = vm.profiles.value.gyroNeutral!!
        val expected = tiltedUp(neutral, 20f, 0f)
        assertEquals(expected.x, saved.x, 0.01f); assertEquals(expected.y, saved.y, 0.01f)
    }

    @Test fun phoneWithoutGyroscopeNeverStarts() = runTest(dispatcher) {
        val noGyro = FakeMotionSource(MotionStatus(hasGyroscope = false, hasAccelerometer = true))
        val v2 = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), emptyList(), { 0L }, motionSource = noGyro)
        v2.onForeground(); v2.updateGyro { it.copy(enabled = true) }; v2.navigate(Screen.CONTROLLER)
        assertFalse(noGyro.running)
        assertFalse(v2.gyroSending.value)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
class GyroUiTest {
    @get:Rule val rule = createComposeRule()
    private val motion = FakeMotionSource()
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        vm = MainViewModel(FakeHid(), MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), emptyList(), { 0L }, motionSource = motion)
        rule.setContent { App(vm, AppActions({}, {}, {}, {})) }
        rule.runOnIdle { vm.onForeground() }
    }

    @Test fun gyroTileOpensTestScreenWithLiveValues() {
        rule.onNodeWithTag("homeGyro").performClick()
        rule.onNodeWithText("Gyro right stick").assertIsDisplayed()
        rule.runOnIdle { assertTrue(motion.running) }
        val n = Vec3(0f, 0.7071f, 0.7071f)
        rule.runOnIdle { motion.hold(n, 30); vm.gyroRecenter(); motion.hold(n, 5); motion.hold(tiltedUp(n, 20f, 0f), 100) }
        rule.waitForIdle()
        rule.onNodeWithText("Gyro is OFF: values are not sent").assertIsDisplayed()
        rule.onNodeWithTag("sw_gyro").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(vm.activeProfile.value.gyro.enabled) }
        rule.onNodeWithText("Sending to the PC as the RIGHT stick").assertIsDisplayed()
    }

    @Test fun controllerShowsGyroIndicatorInsteadOfTouchStick() {
        rule.runOnIdle { vm.updateGyro { it.copy(enabled = true) }; vm.navigate(Screen.CONTROLLER) }
        rule.waitForIdle()
        rule.onNodeWithTag("gyro_indicator").assertIsDisplayed()
        rule.onNodeWithTag("stick_right").assertDoesNotExist()
        rule.onNodeWithTag("chip_Recenter").assertIsDisplayed()
        rule.runOnIdle { vm.updateGyro { it.copy(enabled = false) } }
        rule.waitForIdle()
        rule.onNodeWithTag("stick_right").assertIsDisplayed()
    }
}
