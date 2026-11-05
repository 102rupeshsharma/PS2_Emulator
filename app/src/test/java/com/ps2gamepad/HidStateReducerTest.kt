package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.bluetooth.HidStateReducer.STATE_CONNECTED
import com.ps2gamepad.bluetooth.HidStateReducer.STATE_CONNECTING
import com.ps2gamepad.bluetooth.HidStateReducer.STATE_DISCONNECTED
import com.ps2gamepad.bluetooth.HidStateReducer.STATE_DISCONNECTING
import com.ps2gamepad.model.ConnectionState
import com.ps2gamepad.model.HidState
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.RegistrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidStateReducerTest {
    private val laptop = HostDevice("AA:BB:CC:DD:EE:FF", "LAPTOP")
    private fun run(vararg events: HidEvent) = events.fold(HidState()) { s, e -> HidStateReducer.reduce(s, e) }

    @Test fun constantsMatchBluetoothProfile() {
        // BluetoothProfile.STATE_* values from the Android SDK
        assertEquals(listOf(0, 1, 2, 3), listOf(STATE_DISCONNECTED, STATE_CONNECTING, STATE_CONNECTED, STATE_DISCONNECTING))
    }

    @Test fun successfulRegistration() {
        val s = run(HidEvent.RegisterRequested, HidEvent.ProxyConnected, HidEvent.AppStatusChanged(true))
        assertEquals(RegistrationState.Registered, s.registration)
        assertEquals(ConnectionState.WaitingForHost, s.connection)
        assertTrue(s.proxyAvailable)
        assertFalse(s.isConnected)
    }

    @Test fun registeringIsShownUntilCallback() {
        val s = run(HidEvent.RegisterRequested)
        assertEquals(RegistrationState.Registering, s.registration)
        assertFalse(s.isConnected)
    }

    @Test fun rejectedRegistration() {
        val s = run(HidEvent.RegisterRequested, HidEvent.RegisterRejected("registerApp() returned false"))
        assertTrue(s.registration is RegistrationState.Failed)
        assertEquals(ConnectionState.Idle, s.connection)
    }

    @Test fun stackRejectsViaCallback() {
        val s = run(HidEvent.RegisterRequested, HidEvent.AppStatusChanged(false))
        assertTrue(s.registration is RegistrationState.Failed)
    }

    @Test fun timeoutOnlyAppliesWhileRegistering() {
        assertTrue(run(HidEvent.RegisterRequested, HidEvent.RegisterTimeout).registration is RegistrationState.Failed)
        assertEquals(RegistrationState.Registered, run(HidEvent.RegisterRequested, HidEvent.AppStatusChanged(true), HidEvent.RegisterTimeout).registration)
    }

    @Test fun connectedOnlyFromRealConnectionCallback() {
        val base = arrayOf(HidEvent.RegisterRequested, HidEvent.AppStatusChanged(true))
        assertFalse(run(*base).isConnected)
        assertFalse(run(*base, HidEvent.ConnectionChanged(laptop, STATE_CONNECTING)).isConnected)
        val s = run(*base, HidEvent.ConnectionChanged(laptop, STATE_CONNECTING), HidEvent.ConnectionChanged(laptop, STATE_CONNECTED))
        assertTrue(s.isConnected)
        assertEquals(laptop, s.connectedHost)
    }

    @Test fun disconnectSequence() {
        val s = run(
            HidEvent.RegisterRequested, HidEvent.AppStatusChanged(true),
            HidEvent.ConnectionChanged(laptop, STATE_CONNECTED),
            HidEvent.ConnectionChanged(laptop, STATE_DISCONNECTING),
        )
        assertEquals(ConnectionState.Disconnecting(laptop), s.connection)
        val d = HidStateReducer.reduce(s, HidEvent.ConnectionChanged(laptop, STATE_DISCONNECTED))
        assertEquals(ConnectionState.Disconnected(laptop), d.connection)
        assertEquals("still registered, host can reconnect", RegistrationState.Registered, d.registration)
    }

    @Test fun bluetoothOffDropsEverything() {
        val s = run(HidEvent.ProxyConnected, HidEvent.RegisterRequested, HidEvent.AppStatusChanged(true),
            HidEvent.ConnectionChanged(laptop, STATE_CONNECTED), HidEvent.BluetoothOff)
        assertFalse(s.isConnected)
        assertFalse(s.proxyAvailable)
        assertEquals(RegistrationState.NotRegistered, s.registration)
        assertEquals(ConnectionState.Disconnected(laptop), s.connection)
    }

    @Test fun stackUnregistersWhileConnected() {
        // e.g. the app went to the background
        val s = run(HidEvent.RegisterRequested, HidEvent.AppStatusChanged(true),
            HidEvent.ConnectionChanged(laptop, STATE_CONNECTED), HidEvent.AppStatusChanged(false))
        assertEquals(RegistrationState.NotRegistered, s.registration)
        assertEquals(ConnectionState.Disconnected(laptop), s.connection)
    }

    @Test fun userUnregister() {
        val s = run(HidEvent.RegisterRequested, HidEvent.AppStatusChanged(true), HidEvent.Unregistered)
        assertEquals(RegistrationState.NotRegistered, s.registration)
        assertEquals(ConnectionState.Idle, s.connection)
    }

    @Test fun proxyLossClearsConnection() {
        val s = run(HidEvent.ProxyConnected, HidEvent.AppStatusChanged(true), HidEvent.ConnectionChanged(laptop, STATE_CONNECTED), HidEvent.ProxyDisconnected)
        assertFalse(s.isConnected); assertFalse(s.proxyAvailable)
    }

    @Test fun connectFailureIsAnError() {
        val s = run(HidEvent.AppStatusChanged(true), HidEvent.ConnectFailed("connect() returned false"))
        assertTrue(s.connection is ConnectionState.Error)
    }

    @Test fun unknownStateIsAnError() {
        assertTrue(run(HidEvent.ConnectionChanged(laptop, 42)).connection is ConnectionState.Error)
    }

    @Test fun hostNameFallsBackToAddress() {
        assertEquals("AA:BB", HostDevice("AA:BB", null).displayName)
        assertEquals("AA:BB", HostDevice("AA:BB", " ").displayName)
    }
}
