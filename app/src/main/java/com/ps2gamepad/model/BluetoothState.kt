package com.ps2gamepad.model

/** State of the phone's local Bluetooth adapter. */
enum class BluetoothState(val label: String) {
    UNAVAILABLE("Bluetooth unavailable"),
    DISABLED("Bluetooth disabled"),
    TURNING_ON("Bluetooth turning on"),
    TURNING_OFF("Bluetooth turning off"),
    ENABLED("Bluetooth enabled"),
}

/** Whether the runtime Bluetooth permissions this app needs are granted. */
enum class PermissionState { GRANTED, REQUIRED }
