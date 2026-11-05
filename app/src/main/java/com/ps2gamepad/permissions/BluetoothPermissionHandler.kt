package com.ps2gamepad.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime Bluetooth permissions by Android version:
 *  - API 31+ (Android 12+): BLUETOOTH_CONNECT (HID register/connect/send, device names) and
 *    BLUETOOTH_ADVERTISE (making the phone discoverable for Windows pairing).
 *  - API 28-30: the install-time BLUETOOTH permission, nothing to request at runtime.
 * Location and BLUETOOTH_SCAN are not needed: the phone never scans.
 */
object BluetoothPermissionHandler {

    @SuppressLint("InlinedApi") // guarded by the SDK check below
    fun requiredPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun hasAll(context: Context): Boolean = missingPermissions(context).isEmpty()
}
