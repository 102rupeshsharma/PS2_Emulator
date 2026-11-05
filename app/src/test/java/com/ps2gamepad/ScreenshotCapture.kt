package com.ps2gamepad

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        val vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), listOf("screenshot"), { 0L }, batteryPercent = { 80 })
        rule.setContent { App(vm, AppActions({}, {}, {}, {})) }
        val host = HostDevice("00:00:00:00:00:01", "LAPTOP (mock)")
        rule.runOnIdle {
            vm.onPermissionsChanged(true); vm.register()
            hid.emit(HidEvent.AppStatusChanged(true)); hid.emit(HidEvent.ConnectionChanged(host, HidStateReducer.STATE_CONNECTED))
        }
        shoot("10_dashboard")
        rule.runOnIdle { vm.navigate(Screen.CONTROLLER) }
        shoot("11_controller_default")
        rule.runOnIdle { vm.setEditMode(true) }
        shoot("12_edit_layout")
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
    }
}
