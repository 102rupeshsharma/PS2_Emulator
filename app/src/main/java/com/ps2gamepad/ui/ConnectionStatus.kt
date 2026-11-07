package com.ps2gamepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ConnectionState
import com.ps2gamepad.model.HidState
import com.ps2gamepad.model.PermissionState
import com.ps2gamepad.model.RegistrationState

object Palette {
    val Background = Color.Black // pure black everywhere: AMOLED pixels off
    val Surface = Color(0xFF1B1D22)
    val SurfaceHigh = Color(0xFF262931)
    val Text = Color(0xFFE6E7EB)
    val TextDim = Color(0xFF9DA1AB)
    val Good = Color(0xFF3FBF6F)
    val Warn = Color(0xFFE0A030)
    val Bad = Color(0xFFE5484D)
    val Info = Color(0xFF4C8DFF)
}

enum class Severity(val color: Color) { GOOD(Palette.Good), WARN(Palette.Warn), BAD(Palette.Bad), INFO(Palette.Info), IDLE(Palette.TextDim) }

/** The one status line that matters most right now, used by the compact controller indicator. */
data class OverallStatus(val text: String, val severity: Severity)

fun overallStatus(bt: BluetoothState, perm: PermissionState, hid: HidState): OverallStatus = when {
    bt == BluetoothState.UNAVAILABLE -> OverallStatus("Bluetooth unavailable", Severity.BAD)
    bt != BluetoothState.ENABLED -> OverallStatus(bt.label, Severity.BAD)
    perm != PermissionState.GRANTED -> OverallStatus("Permission required", Severity.WARN)
    hid.registration is RegistrationState.Failed -> OverallStatus("Registration failed", Severity.BAD)
    hid.registration == RegistrationState.Registering -> OverallStatus("Registering HID", Severity.INFO)
    hid.registration == RegistrationState.NotRegistered -> OverallStatus("Not registered", Severity.WARN)
    else -> when (val c = hid.connection) {
        is ConnectionState.Connected -> OverallStatus("Connected: ${c.host.displayName}", Severity.GOOD)
        is ConnectionState.Connecting -> OverallStatus("Connecting", Severity.INFO)
        is ConnectionState.Disconnecting -> OverallStatus("Disconnecting", Severity.WARN)
        is ConnectionState.Disconnected -> OverallStatus("Disconnected", Severity.BAD)
        is ConnectionState.Error -> OverallStatus("Connection error", Severity.BAD)
        ConnectionState.WaitingForHost -> OverallStatus("Waiting for pairing", Severity.WARN)
        ConnectionState.Idle -> OverallStatus("Not connected", Severity.WARN)
    }
}

fun registrationSeverity(r: RegistrationState) = when (r) {
    RegistrationState.Registered -> Severity.GOOD
    RegistrationState.Registering -> Severity.INFO
    is RegistrationState.Failed -> Severity.BAD
    RegistrationState.NotRegistered -> Severity.IDLE
}

fun connectionSeverity(c: ConnectionState) = when (c) {
    is ConnectionState.Connected -> Severity.GOOD
    is ConnectionState.Connecting -> Severity.INFO
    ConnectionState.WaitingForHost -> Severity.WARN
    is ConnectionState.Disconnecting, is ConnectionState.Disconnected -> Severity.WARN
    is ConnectionState.Error -> Severity.BAD
    ConnectionState.Idle -> Severity.IDLE
}

@Composable
fun StatusDot(severity: Severity, size: Int = 10) {
    Box(Modifier.size(size.dp).background(severity.color, CircleShape))
}

@Composable
fun StatusRow(title: String, value: String, severity: Severity) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 1.dp)) {
        StatusDot(severity)
        Text("$title:", color = Palette.TextDim, fontSize = 12.sp)
        Text(value, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Compact pill for the controller screen. */
@Composable
fun ConnectionChip(status: OverallStatus, modifier: Modifier = Modifier, bg: Color = Color(0xCC000000), fg: Color = Palette.Text) {
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(status.severity, 8)
        Text(status.text, color = fg, fontSize = 11.sp, maxLines = 1)
    }
}
