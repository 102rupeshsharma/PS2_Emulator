package com.ps2gamepad.bluetooth

import com.ps2gamepad.model.ConnectionState
import com.ps2gamepad.model.HidState
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.model.RegistrationState

/** Events coming from the Android Bluetooth stack (or from our own calls into it). */
sealed interface HidEvent {
    data object ProxyConnected : HidEvent
    data object ProxyDisconnected : HidEvent
    data object RegisterRequested : HidEvent
    data class RegisterRejected(val reason: String) : HidEvent
    data object RegisterTimeout : HidEvent
    data class AppStatusChanged(val registered: Boolean) : HidEvent
    data object Unregistered : HidEvent
    /** [state] uses the BluetoothProfile.STATE_* values (0 = disconnected ... 3 = disconnecting). */
    data class ConnectionChanged(val host: HostDevice, val state: Int) : HidEvent
    data class ConnectFailed(val reason: String) : HidEvent
    data object BluetoothOff : HidEvent
}

/**
 * Pure state machine for HID registration and connection, kept apart from the Android callbacks
 * so it can be unit tested. Only a real connection callback (ConnectionChanged with STATE_CONNECTED)
 * produces [ConnectionState.Connected].
 */
object HidStateReducer {
    const val STATE_DISCONNECTED = 0
    const val STATE_CONNECTING = 1
    const val STATE_CONNECTED = 2
    const val STATE_DISCONNECTING = 3

    fun reduce(s: HidState, e: HidEvent): HidState = when (e) {
        HidEvent.ProxyConnected -> s.copy(proxyAvailable = true)

        HidEvent.ProxyDisconnected -> HidState(proxyAvailable = false, registration = lostRegistration(s), connection = disconnected(s))

        HidEvent.RegisterRequested -> s.copy(registration = RegistrationState.Registering)

        is HidEvent.RegisterRejected -> s.copy(registration = RegistrationState.Failed(e.reason), connection = ConnectionState.Idle)

        HidEvent.RegisterTimeout ->
            if (s.registration == RegistrationState.Registering)
                s.copy(registration = RegistrationState.Failed("no registration callback within timeout"), connection = ConnectionState.Idle)
            else s

        is HidEvent.AppStatusChanged ->
            if (e.registered) {
                s.copy(
                    registration = RegistrationState.Registered,
                    connection = if (s.connection is ConnectionState.Connected || s.connection is ConnectionState.Connecting) s.connection
                    else ConnectionState.WaitingForHost,
                )
            } else {
                // Unregistered by us, by the stack (app went to background, another app registered, BT off).
                val reg = if (s.registration == RegistrationState.Registering)
                    RegistrationState.Failed("stack rejected the HID application")
                else RegistrationState.NotRegistered
                s.copy(registration = reg, connection = if (s.connection is ConnectionState.Connected) disconnected(s) else ConnectionState.Idle)
            }

        HidEvent.Unregistered -> s.copy(registration = RegistrationState.NotRegistered, connection = ConnectionState.Idle)

        is HidEvent.ConnectionChanged -> s.copy(
            connection = when (e.state) {
                STATE_CONNECTED -> ConnectionState.Connected(e.host)
                STATE_CONNECTING -> ConnectionState.Connecting(e.host)
                STATE_DISCONNECTING -> ConnectionState.Disconnecting(e.host)
                STATE_DISCONNECTED ->
                    if (s.registration == RegistrationState.Registered) ConnectionState.Disconnected(e.host) else ConnectionState.Idle
                else -> ConnectionState.Error("unknown connection state ${e.state}")
            }
        )

        is HidEvent.ConnectFailed -> s.copy(connection = ConnectionState.Error(e.reason))

        HidEvent.BluetoothOff -> HidState(proxyAvailable = false, registration = lostRegistration(s), connection = disconnected(s))
    }

    private fun lostRegistration(s: HidState): RegistrationState =
        if (s.registration is RegistrationState.Failed) s.registration else RegistrationState.NotRegistered

    private fun disconnected(s: HidState): ConnectionState = when (val c = s.connection) {
        is ConnectionState.Connected -> ConnectionState.Disconnected(c.host)
        is ConnectionState.Connecting -> ConnectionState.Disconnected(c.host)
        is ConnectionState.Disconnecting -> ConnectionState.Disconnected(c.host)
        is ConnectionState.Disconnected -> c
        else -> ConnectionState.Idle
    }
}
