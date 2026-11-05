package com.ps2gamepad.ui

import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ps2gamepad.bluetooth.BluetoothManagerHelper
import com.ps2gamepad.bluetooth.HidDeviceManager
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.permissions.BluetoothPermissionHandler
import com.ps2gamepad.settings.DataStoreTextStorage
import com.ps2gamepad.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { factory() }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged(BluetoothPermissionHandler.hasAll(this))
        vm.autoStart()
    }
    private val enableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val discoverableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // resultCode = discoverable duration in seconds, or RESULT_CANCELED.
        vm.log.add(if (result.resultCode > 0) "Phone discoverable for ${result.resultCode} s - pair from Windows now" else "Discoverable request declined")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersive()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.screen.collect { s ->
                    if (s == Screen.CONTROLLER) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            App(
                vm = vm,
                actions = AppActions(
                    requestPermissions = { permissionLauncher.launch(BluetoothPermissionHandler.requiredPermissions()) },
                    enableBluetooth = { safeLaunch { enableLauncher.launch(BluetoothManagerHelper.enableIntent()) } },
                    makeDiscoverable = { safeLaunch { discoverableLauncher.launch(BluetoothManagerHelper.discoverableIntent()) } },
                    exit = { finish() },
                ),
            )
        }
    }

    private inline fun safeLaunch(block: () -> Unit) {
        try { block() } catch (e: Exception) { vm.log.add("Could not open system dialog: ${e.message}") }
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        vm.onPermissionsChanged(BluetoothPermissionHandler.hasAll(this))
        vm.onForeground()
        vm.autoStart()
    }

    override fun onPause() {
        vm.onInterrupted("app paused")
        super.onPause()
    }

    override fun onStop() {
        vm.onBackground()
        vm.flushSettings()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { enterImmersive(); vm.onFocusGained() } else vm.onInterrupted("window lost focus")
    }

    private fun enterImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun factory() = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = applicationContext
            val log = DiagnosticLog()
            // Logcat only in debuggable builds: messages can contain Bluetooth device names.
            val debuggable = (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val logger: (String) -> Unit = { msg -> if (debuggable) Log.i(TAG, msg); log.add(msg) }
            // Process-wide scope so pending settings are still written after the ViewModel is cleared.
            val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val battery = app.getSystemService(BatteryManager::class.java)
            val bt = BluetoothManagerHelper(app).also { it.start() }
            val hid = HidDeviceManager(app, { bt.adapter }, logger)
            return MainViewModel(
                hid = hid,
                bluetoothState = bt.state,
                bondedHostsProvider = bt::bondedHosts,
                log = log,
                compatibility = bt.compatibilitySummary() +
                    (if (bt.state.value == BluetoothState.UNAVAILABLE) listOf("STOP: this phone has no usable Bluetooth adapter") else emptyList()),
                uptimeMs = SystemClock::uptimeMillis,
                cleanup = { bt.stop() },
                settingsRepository = SettingsRepository(DataStoreTextStorage(app), settingsScope),
                phoneNameControl = com.ps2gamepad.bluetooth.AdapterPhoneName(app) { bt.adapter },
                motionSource = com.ps2gamepad.motion.AndroidMotionSource(app),
                batteryPercent = {
                    battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
                },
            ) as T
        }
    }

    companion object { const val TAG = "BtGamepad" }
}
