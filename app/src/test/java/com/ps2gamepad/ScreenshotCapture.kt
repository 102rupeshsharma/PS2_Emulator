package com.ps2gamepad

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControllerBackground
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.Stick
import com.ps2gamepad.ui.App
import com.ps2gamepad.ui.AppActions
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real Compose screens with Robolectric's native graphics and writes PNGs to
 * docs/screenshots. Runs only with -Pscreenshots (skipped otherwise). The Bluetooth state shown
 * is MOCK state (see FakeHid); "Connected" here is a staged picture, not a hardware result.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class ScreenshotCapture {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    private fun shoot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        out.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun captureScreens() {
        assumeTrue("run with -Pscreenshots", System.getProperty("screenshotDir") != null)
        val hid = FakeHid()
        val motion = FakeMotionSource()
        val vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), listOf("screenshot"), { 0L },
            batteryPercent = { 80 }, phoneNameControl = FakePhoneName(), motionSource = motion)
        rule.setContent { App(vm, AppActions({}, {}, {}, {})) }
        val host = HostDevice("00:00:00:00:00:01", "LAPTOP (mock)")
        shoot("00_home_permission")
        rule.runOnIdle { vm.onPermissionsChanged(true); vm.onForeground(); vm.autoStart(); hid.emit(HidEvent.AppStatusChanged(true)) }
        shoot("01_home_ready_to_pair")
        rule.runOnIdle {
            vm.onPermissionsChanged(true); vm.register()
            hid.emit(HidEvent.AppStatusChanged(true)); hid.emit(HidEvent.ConnectionChanged(host, HidStateReducer.STATE_CONNECTED))
        }
        shoot("02_home_connected")
        rule.runOnIdle {
            vm.createLoadout("God of War"); vm.updateAppearance { it.copy(buttonScale = 1.2f) }; vm.setEditMode(false)
            vm.createLoadout("GTA San Andreas"); vm.updateAppearance { it.copy(buttonOpacity = 0.7f) }
            vm.updateGyro { it.copy(enabled = true) }; vm.setEditMode(false)
            vm.chooseMode(com.ps2gamepad.model.GamingMode.EMULATOR)
        }
        shoot("07_loadouts")
        rule.runOnIdle { vm.playLoadout(com.ps2gamepad.settings.DefaultProfiles.STANDARD); vm.updateGyro { it.copy(enabled = false) } }
        val n = com.ps2gamepad.motion.Vec3(0f, 0.7071f, 0.7071f)
        rule.runOnIdle {
            vm.onForeground(); vm.updateGyro { it.copy(enabled = true) }; vm.navigate(Screen.GYRO)
            motion.hold(n, 30); vm.gyroRecenter(); motion.hold(n, 5); motion.hold(tiltedUp(n, 12f, -9f), 60)
        }
        shoot("05_gyro_screen")
        rule.runOnIdle { vm.navigate(Screen.CONTROLLER); motion.hold(tiltedUp(n, 12f, -9f), 5) }
        shoot("06_controller_gyro")
        rule.onNodeWithTag("chip_Settings").performClick()
        shoot("08_controller_settings")
        rule.onNodeWithTag("chip_Close").performClick()
        rule.runOnIdle { vm.updateGyro { it.copy(enabled = false) } }
        rule.runOnIdle { vm.navigate(Screen.DASHBOARD) }
        shoot("10_dashboard")
        rule.runOnIdle { vm.navigate(Screen.CONTROLLER) }
        shoot("11_controller_default")
        rule.runOnIdle { vm.setEditMode(true) }
        shoot("12_edit_layout")
        rule.runOnIdle {
            vm.setEditMode(false)
            vm.saveLayout(emptyMap(), listOf(com.ps2gamepad.model.ExtraButton("x1", com.ps2gamepad.model.ControllerButton.CROSS, 0.62f, 0.2f)))
        }
        shoot("19_controller_added_button")
        rule.runOnIdle { vm.saveLayout(emptyMap(), emptyList()); vm.setDarkControls(true) }
        shoot("19b_controller_black_theme")
        rule.runOnIdle { vm.setDarkControls(false) }
        rule.runOnIdle {
            vm.setEditMode(false)
            vm.updateAppearance { it.copy(buttonScale = 1.15f, buttonOpacity = 0.7f, background = ControllerBackground.BLUE, showLabels = false) }
            vm.navigate(Screen.CONTROLLER)
        }
        shoot("13_controller_customized")
        rule.runOnIdle { vm.resetAppearance(); vm.navigate(Screen.CUSTOMIZE) }
        shoot("14_customize")
        rule.runOnIdle { vm.navigate(Screen.PROFILES) }
        shoot("15_profiles")
        rule.runOnIdle { vm.updateStick(Stick.LEFT) { it.copy(floating = false) }; vm.navigate(Screen.CALIBRATION) }
        shoot("16_calibration")
        rule.runOnIdle { vm.navigate(Screen.DIAGNOSTICS) }
        shoot("17_diagnostics")
        rule.runOnIdle { vm.navigate(Screen.CONNECTION) }
        shoot("18_connection")
        rule.runOnIdle { vm.openController() }
        shoot("20_mode_select")
        rule.runOnIdle { vm.setPcInputEnabled(true); vm.chooseMode(com.ps2gamepad.model.GamingMode.PC) }
        shoot("21_pc_controller")
        rule.runOnIdle { vm.setDarkControls(true) }
        shoot("21b_pc_controller_black")
        rule.runOnIdle { vm.setDarkControls(false) }
        rule.runOnIdle { vm.setPcEditMode(true) }
        shoot("22_pc_edit")
        rule.runOnIdle { vm.setPcEditMode(false); vm.navigate(Screen.PC_PROFILES) }
        shoot("23_pc_profiles")
    }
}
