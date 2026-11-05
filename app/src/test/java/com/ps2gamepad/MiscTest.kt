package com.ps2gamepad

import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ConnectionState
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.HidState
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.PermissionState
import com.ps2gamepad.model.RegistrationState
import com.ps2gamepad.permissions.BluetoothPermissionHandler
import com.ps2gamepad.ui.Severity
import com.ps2gamepad.ui.overallStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    @Test fun permissionsPerApiLevel() {
        assertEquals(0, BluetoothPermissionHandler.requiredPermissions(28).size)
        assertEquals(0, BluetoothPermissionHandler.requiredPermissions(30).size)
        assertArrayEquals(
            arrayOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_ADVERTISE"),
            BluetoothPermissionHandler.requiredPermissions(31),
        )
        assertEquals(2, BluetoothPermissionHandler.requiredPermissions(35).size)
    }

    @Test fun logRingBuffer() {
        val log = DiagnosticLog(capacity = 3, now = { 0L })
        (1..5).forEach { log.add("m$it") }
        assertEquals(3, log.lines.value.size)
        assertTrue(log.lines.value.last().endsWith("m5"))
        assertTrue(log.lines.value.first().endsWith("m3"))
    }

    @Test fun overallStatusNeverClaimsConnectedWithoutConnection() {
        val registered = HidState(true, RegistrationState.Registered, ConnectionState.WaitingForHost)
        assertEquals("Waiting for pairing", overallStatus(BluetoothState.ENABLED, PermissionState.GRANTED, registered).text)
        val connected = registered.copy(connection = ConnectionState.Connected(HostDevice("A", "PC")))
        assertEquals(Severity.GOOD, overallStatus(BluetoothState.ENABLED, PermissionState.GRANTED, connected).severity)
        // Bluetooth off overrides a stale connected state
        assertEquals(Severity.BAD, overallStatus(BluetoothState.DISABLED, PermissionState.GRANTED, connected).severity)
        assertEquals("Permission required", overallStatus(BluetoothState.ENABLED, PermissionState.REQUIRED, HidState()).text)
        assertEquals("Bluetooth unavailable", overallStatus(BluetoothState.UNAVAILABLE, PermissionState.GRANTED, HidState()).text)
        assertEquals("Registration failed",
            overallStatus(BluetoothState.ENABLED, PermissionState.GRANTED, HidState(registration = RegistrationState.Failed("x"))).text)
    }
}
