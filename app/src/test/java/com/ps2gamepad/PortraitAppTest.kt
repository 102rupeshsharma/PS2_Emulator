package com.ps2gamepad

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.ui.App
import com.ps2gamepad.ui.AppActions
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import com.ps2gamepad.ui.orientationFor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 3.4.0: the app is portrait, only the controller is landscape. */
class OrientationRuleTest {
    @Test fun onlyTheControllerIsLandscape() {
        for (s in Screen.entries) {
            val expected = if (s == Screen.CONTROLLER) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            assertEquals("$s", expected, orientationFor(s))
        }
    }

    @Test fun theAppStartsInPortrait() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue("manifest starts the activity in portrait", manifest.contains("android:screenOrientation=\"sensorPortrait\""))
    }

    @Test fun readmeHasNoPaymentSection() {
        val readme = java.io.File("../README.md").readText()
        for (word in listOf("UPI", "upi-qr", "Support this project", "okicici", "superyes")) {
            assertFalse("README still mentions $word", readme.contains(word))
        }
        assertFalse("QR image deleted", java.io.File("../docs/support/upi-qr.png").exists())
    }
}

/**
 * Real UI, MOCK Bluetooth, on a PORTRAIT phone (411 x 891 dp): every screen opens from the menu like a
 * user would, and its main buttons can be reached (scrolling where needed) and work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port")
class PortraitScreensUiTest {
    @get:Rule val rule = createComposeRule()
    private val hid = FakeHid()
    private val laptop = HostDevice("11:22", "LAPTOP", isComputer = true)
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { listOf(laptop) }, DiagnosticLog(), emptyList(), { 0L }, phoneNameControl = FakePhoneName())
        rule.setContent { App(vm, AppActions({}, {}, {}, {})) }
        rule.runOnIdle { vm.onPermissionsChanged(true); vm.onForeground(); vm.autoStart() }
    }

    private fun connect() = rule.runOnIdle {
        vm.connectNow(); hid.emit(HidEvent.AppStatusChanged(true))
        hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED))
    }

    private fun openMore() {
        rule.onNodeWithTag("homeMore").performScrollTo().performClick()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
    }

    @Test fun homeConnectThenOpenTheControllerInPortrait() {
        rule.onNodeWithText("Connect").assertIsDisplayed()
        connect()
        rule.onNodeWithText("Open Controller").performClick()
        rule.runOnIdle { assertEquals(Screen.LOADOUTS, vm.screen.value) }
        rule.onNodeWithTag("play_standard").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(Screen.CONTROLLER, vm.screen.value) }
    }

    @Test fun everyMenuButtonIsReachableAndOpensItsScreen() {
        val targets = listOf(
            "dash_connection" to Screen.CONNECTION, "dash_customize" to Screen.CUSTOMIZE, "dash_profiles" to Screen.PROFILES,
            "dash_diagnostics" to Screen.DIAGNOSTICS, "dash_calibration" to Screen.CALIBRATION,
        )
        for ((tag, screen) in targets) {
            rule.runOnIdle { vm.navigate(Screen.HOME) }
            openMore()
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.runOnIdle { assertEquals(tag, screen, vm.screen.value) }
        }
    }

    @Test fun customizeInPortraitChangesSettingsBelowThePreview() {
        rule.runOnIdle { vm.navigate(Screen.CUSTOMIZE) }
        rule.onNodeWithTag("preview").assertIsDisplayed()
        rule.onNodeWithTag("sw_labels").performScrollTo().performClick()
        rule.runOnIdle { assertFalse(vm.activeProfile.value.appearance.showLabels) }
        rule.onNodeWithTag("op_DPAD").performScrollTo().performClick()
        rule.onNodeWithTag("tab_RIGHT").performScrollTo().performClick()
        rule.onNodeWithTag("sw_invert").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(vm.activeProfile.value.right.invertY) }
    }

    @Test fun profilesInPortraitButtonsWrapAndWork() {
        rule.runOnIdle { vm.navigate(Screen.PROFILES) }
        rule.waitForIdle()
        // the buttons of a profile further down the list are reachable and work
        val target = vm.profiles.value.profiles.last { !it.builtIn || it.id != vm.profiles.value.activeId }
        rule.onNodeWithTag("profile_${target.id}").performScrollTo()
        rule.onNodeWithTag("use_${target.id}", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(target.id, vm.profiles.value.activeId) }
    }

    @Test fun connectionScreenInPortraitShowsTheLogAndButtons() {
        connect()
        rule.runOnIdle { vm.navigate(Screen.CONNECTION) }
        rule.onNodeWithText("Disconnect").performScrollTo().performClick()
        rule.onNodeWithText("Diagnostic log").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Release All Buttons").performScrollTo().performClick()
    }

    @Test fun diagnosticsAndCalibrationInPortrait() {
        rule.runOnIdle { vm.navigate(Screen.DIAGNOSTICS) }
        rule.onNodeWithText("Reset All Inputs").performScrollTo().performClick()
        rule.onNodeWithText("Reset counters").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { vm.navigate(Screen.CALIBRATION) }
        rule.onNodeWithTag("calStick_LEFT").assertIsDisplayed()
        rule.onNodeWithTag("calStick_RIGHT").performScrollTo().assertIsDisplayed()
    }
}
