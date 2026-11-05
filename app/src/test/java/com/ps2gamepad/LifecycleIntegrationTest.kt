package com.ps2gamepad

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ps2gamepad.bluetooth.BluetoothManagerHelper
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.GamepadState
import com.ps2gamepad.ui.MainActivity
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Integration tests of the REAL MainActivity, ViewModel factory, DataStore and Bluetooth classes,
 * running on Robolectric's simulated Android framework. The Bluetooth adapter here is
 * Robolectric's SHADOW (no radio): these verify lifecycle wiring, not hardware behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
class LifecycleIntegrationTest {

    private fun vmOf(s: ActivityScenario<MainActivity>): MainViewModel {
        var vm: MainViewModel? = null
        s.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
        return vm!!
    }

    @Test fun launchesToDashboardWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(Lifecycle.State.RESUMED)
            val vm = vmOf(s)
            assertEquals(Screen.HOME, vm.screen.value)
            assertNotNull(vm.compatibility.firstOrNull { it.startsWith("Android") })
        }
    }

    @Test fun pauseAndStopReleaseHeldInputs() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(Lifecycle.State.RESUMED)
            val vm = vmOf(s)
            s.onActivity { vm.navigate(Screen.CONTROLLER); vm.input.setButton(ControllerButton.CROSS, true) }
            s.moveToState(Lifecycle.State.STARTED) // onPause
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
            s.onActivity { vm.input.setButton(ControllerButton.L1, true) }
            s.moveToState(Lifecycle.State.CREATED) // onStop
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        }
    }

    @Test fun windowFocusLossReleasesInputs() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(Lifecycle.State.RESUMED)
            val vm = vmOf(s)
            s.onActivity {
                vm.input.setButton(ControllerButton.R2, true)
                it.onWindowFocusChanged(false) // e.g. notification shade pulled down
            }
            assertEquals(GamepadState.NEUTRAL, vm.gamepad.value)
        }
    }

    @Test fun settingsSurviveActivityRecreationAndProcessRestartStorage() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(Lifecycle.State.RESUMED)
            val vm = vmOf(s)
            s.onActivity { vm.updateAppearance { a -> a.copy(buttonOpacity = 0.42f) } }
            s.moveToState(Lifecycle.State.CREATED) // onStop -> flush to DataStore
        }
        // The write is asynchronous (DataStore IO thread): wait until it is on disk, as a real
        // process restart would only happen afterwards.
        val storage = com.ps2gamepad.settings.DataStoreTextStorage(ApplicationProvider.getApplicationContext())
        var onDisk: String? = null
        for (i in 0 until 100) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            onDisk = kotlinx.coroutines.runBlocking { storage.read() }
            if (onDisk?.contains("0.42") == true) break
            Thread.sleep(20)
        }
        org.junit.Assert.assertTrue("setting written to DataStore: $onDisk", onDisk?.contains("0.42") == true)
        // New activity + new ViewModel reading the same DataStore file
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(Lifecycle.State.RESUMED)
            val vm = vmOf(s)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            var waited = 0
            while (!vm.settings.loaded.value && waited++ < 100) { Thread.sleep(20); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            assertEquals(0.42f, vm.activeProfile.value.appearance.buttonOpacity, 0.001f)
        }
    }

    @Test fun bluetoothHelperTracksAdapterBroadcasts() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val helper = BluetoothManagerHelper(ctx)
        helper.start()
        fun send(state: Int) {
            ctx.sendBroadcast(Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE, state))
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        send(BluetoothAdapter.STATE_TURNING_OFF); assertEquals(BluetoothState.TURNING_OFF, helper.state.value)
        send(BluetoothAdapter.STATE_OFF); assertEquals(BluetoothState.DISABLED, helper.state.value)
        send(BluetoothAdapter.STATE_ON); assertEquals(BluetoothState.ENABLED, helper.state.value)
        helper.stop()
        send(BluetoothAdapter.STATE_OFF)
        assertEquals("no updates after stop() (receiver unregistered)", BluetoothState.ENABLED, helper.state.value)
    }

    // ---------------------------------------------------------------- audit regressions (Robolectric shadow adapter, no radio)

    @Test fun hidManagerCloseIsIdempotentAndLaterCallsAreIgnored() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val adapter = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        val m = com.ps2gamepad.bluetooth.HidDeviceManager(ctx, { adapter }, {})
        org.junit.Assert.assertFalse("no host -> report dropped", m.sendReport(com.ps2gamepad.bluetooth.HidReportManager.NEUTRAL_REPORT, 0))
        m.close()
        m.close() // second close must not throw
        // Calls after close (e.g. a late Bluetooth broadcast) must not throw RejectedExecutionException
        m.register(); m.onBluetoothOff(); m.connect("00:11:22:33:44:55"); m.disconnect(); m.unregister()
        org.junit.Assert.assertFalse(m.sendReport(com.ps2gamepad.bluetooth.HidReportManager.NEUTRAL_REPORT, 0))
    }
}
