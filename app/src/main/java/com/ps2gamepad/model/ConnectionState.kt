package com.ps2gamepad.model

/** HID application registration with the Android Bluetooth stack. */
sealed interface RegistrationState {
    val label: String

    data object NotRegistered : RegistrationState { override val label = "Not registered" }
    data object Registering : RegistrationState { override val label = "Registering HID" }
    data object Registered : RegistrationState { override val label = "HID registration successful" }
    data class Failed(val reason: String) : RegistrationState {
        override val label = "HID registration failed: $reason"
    }
}

/**
 * Connection between this phone (HID device) and a host such as the Windows laptop.
 * [Connected] is only ever produced by a real BluetoothHidDevice connection callback.
 */
sealed interface ConnectionState {
    val label: String

    /** Not registered, so no host can connect. */
    data object Idle : ConnectionState { override val label = "Not available (register first)" }
    /** Registered and waiting for Windows to pair / connect. */
    data object WaitingForHost : ConnectionState { override val label = "Waiting for pairing / host connection" }
    data class Connecting(val host: HostDevice) : ConnectionState { override val label = "Connecting to ${host.displayName}" }
    data class Connected(val host: HostDevice) : ConnectionState { override val label = "Connected to ${host.displayName}" }
    data class Disconnecting(val host: HostDevice) : ConnectionState { override val label = "Disconnecting from ${host.displayName}" }
    data class Disconnected(val host: HostDevice?) : ConnectionState {
        override val label = "Disconnected" + (host?.let { " from ${it.displayName}" } ?: "")
    }
    data class Error(val reason: String) : ConnectionState { override val label = "Connection error: $reason" }
}

/**
 * A remote Bluetooth host. [name] may be null when the permission or the stack does not give it.
 * [isComputer]: its Bluetooth class says "computer" (laptops/PCs), so earbuds etc. can be hidden.
 */
data class HostDevice(val address: String, val name: String?, val isComputer: Boolean = true) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: address
}

/** Combined state published by the HID device manager. */
data class HidState(
    val proxyAvailable: Boolean = false,
    val registration: RegistrationState = RegistrationState.NotRegistered,
    val connection: ConnectionState = ConnectionState.Idle,
) {
    val connectedHost: HostDevice? get() = (connection as? ConnectionState.Connected)?.host
    val isConnected: Boolean get() = connection is ConnectionState.Connected
}
