package com.ps2gamepad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.HostDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Adapter access, adapter on/off tracking, bonded hosts and the device compatibility summary. */
class BluetoothManagerHelper(private val context: Context) {

    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                _state.value = map(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR))
            }
        }
    }

    fun start() {
        if (receiverRegistered) return
        // ACTION_STATE_CHANGED is a protected system broadcast; NOT_EXPORTED is correct and safe.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        refresh()
    }

    fun stop() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    fun refresh() { _state.value = readState() }

    private fun readState(): BluetoothState {
        val a = adapter ?: return BluetoothState.UNAVAILABLE
        return map(runCatching { a.state }.getOrDefault(BluetoothAdapter.STATE_OFF))
    }

    private fun map(s: Int) = when (s) {
        BluetoothAdapter.STATE_ON -> BluetoothState.ENABLED
        BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
        BluetoothAdapter.STATE_OFF -> BluetoothState.DISABLED
        else -> if (adapter == null) BluetoothState.UNAVAILABLE else BluetoothState.DISABLED
    }

    /** Bonded (paired) devices that could be HID hosts. Requires BLUETOOTH_CONNECT on API 31+. */
    @SuppressLint("MissingPermission")
    fun bondedHosts(): List<HostDevice> = try {
        adapter?.bondedDevices.orEmpty()
            .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE } // HID device profile is classic BR/EDR
            .map { HostDevice(it.address, it.name) }
            .sortedBy { it.displayName.lowercase() }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Lines for the compatibility section and log. Facts only, nothing is assumed. */
    fun compatibilitySummary(): List<String> = buildList {
        add("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        add("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
        add("BluetoothHidDevice API (needs API 28+): present (minSdk 28)")
        add("Bluetooth adapter: " + if (adapter != null) "present" else "NOT PRESENT")
        add("HID Device profile: tested at registration (getProfileProxy + registerApp)")
    }

    companion object {
        fun discoverableIntent(seconds: Int = 300): Intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds)

        fun enableIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
}
