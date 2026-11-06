package com.ps2gamepad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.model.RegistrationState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Home screen. Everything shown is real state; nothing is estimated or fabricated. */
@Composable
fun DashboardScreen(vm: MainViewModel, actions: AppActions) {
    val bt by vm.bluetoothState.collectAsStateWithLifecycle()
    val perm by vm.permission.collectAsStateWithLifecycle()
    val hid by vm.hidState.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val store by vm.profiles.collectAsStateWithLifecycle()
    val since by vm.connectedSince.collectAsStateWithLifecycle()

    // Ticks once per second only for the connection timer and battery; nothing else recomposes.
    var now by remember { mutableLongStateOf(vm.uptimeNow()) }
    var battery by remember { mutableStateOf(vm.batteryPercent()) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            now = vm.uptimeNow()
            if (n++ % 30 == 0) battery = vm.batteryPercent()
            delay(1000)
        }
    }

    BackHandler { vm.navigate(Screen.HOME) }
    val status = overallStatus(bt, perm, hid)

    Row(
        Modifier.fillMaxSize().background(Palette.Background).windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp).testTag("dashboard"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Back", outlined = true) { vm.navigate(Screen.HOME) }
                Text("More", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Card {
                StatusRow("Bluetooth", bt.label, if (bt == com.ps2gamepad.model.BluetoothState.ENABLED) Severity.GOOD else Severity.BAD)
                StatusRow("Controller", status.text, status.severity)
                StatusRow("HID registration", hid.registration.label, registrationSeverity(hid.registration))
                StatusRow("Connected to", hid.connectedHost?.displayName ?: "-", if (hid.isConnected) Severity.GOOD else Severity.IDLE)
                StatusRow("Connected for", since?.let { formatDuration(now - it) } ?: "-", Severity.IDLE)
                StatusRow("Last successful connection", store.lastConnection?.let {
                    "${it.hostName}, " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.epochMillis))
                } ?: "never", Severity.IDLE)
                StatusRow("Active profile", profile.name, Severity.INFO)
                StatusRow("Phone battery", battery?.let { "$it %" } ?: "unknown", Severity.IDLE)
            }
            (hid.registration as? RegistrationState.Failed)?.let {
                Text("Registration failed: ${it.reason}. See BLUETOOTH_SETUP.md, troubleshooting.", color = Palette.Bad, fontSize = 12.sp)
            }
            (hid.connection as? com.ps2gamepad.model.ConnectionState.Error)?.let {
                Text("Connection error: ${it.reason}", color = Palette.Bad, fontSize = 12.sp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick access", color = Palette.TextDim, fontSize = 12.sp)
            BigButton("Open Controller", "dash_controller") { vm.openController() }
            BigButton("Connection (register / pair / connect)", "dash_connection", outlined = true) { vm.navigate(Screen.CONNECTION) }
            BigButton("Customize controller", "dash_customize", outlined = true) { vm.navigate(Screen.CUSTOMIZE) }
            BigButton("Profiles", "dash_profiles", outlined = true) { vm.navigate(Screen.PROFILES) }
            BigButton("Input test / diagnostics", "dash_diagnostics", outlined = true) { vm.navigate(Screen.DIAGNOSTICS) }
            BigButton("Analog calibration", "dash_calibration", outlined = true) { vm.navigate(Screen.CALIBRATION) }
            BigButton("Gyro right stick", "dash_gyro", outlined = true) { vm.navigate(Screen.GYRO) }
            BigButton("Restore my phone's original Bluetooth name", "dash_restoreName", outlined = true) { vm.restorePhoneName() }
        }
    }
}

@Composable
private fun BigButton(text: String, tag: String, outlined: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.testTag(tag)) { ActionButton(text, outlined = outlined, onClick = onClick) }
}
