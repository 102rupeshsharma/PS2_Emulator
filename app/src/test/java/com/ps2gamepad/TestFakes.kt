package com.ps2gamepad

import com.ps2gamepad.bluetooth.HidController
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.bluetooth.SenderStats
import com.ps2gamepad.model.HidState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MOCK Bluetooth HID layer, driven by the real state reducer. Tests using it verify app logic
 * only; they say nothing about real Bluetooth hardware.
 */
class FakeHid : HidController {
    var powerSaving: Boolean? = null
    override fun setPowerSaving(on: Boolean) { powerSaving = on }
    val s = MutableStateFlow(HidState())
    override val state: StateFlow<HidState> = s
    override val stats: StateFlow<SenderStats> = MutableStateFlow(SenderStats())
    var registerCalls = 0; var unregisterCalls = 0; var bluetoothOffCalls = 0; var closed = false
    val connects = mutableListOf<String>()
    val sent = mutableListOf<ByteArray>()
    fun emit(e: HidEvent) { s.value = HidStateReducer.reduce(s.value, e) }
    var lastAutoConnect = true
    override fun register(autoConnectPlugged: Boolean) {
        registerCalls++; lastAutoConnect = autoConnectPlugged; emit(HidEvent.RegisterRequested)
    }
    override fun unregister() { unregisterCalls++; emit(HidEvent.Unregistered) }
    override fun connect(address: String) { connects += address }
    override fun disconnect() {}
    override fun sendReport(report: ByteArray, eventUptimeMs: Long): Boolean {
        if (!s.value.isConnected) return false
        sent += report; return true
    }
    override fun onBluetoothOff() { bluetoothOffCalls++; emit(HidEvent.BluetoothOff) }
    override fun close() { closed = true }
}

/** MOCK motion sensors: tests push samples by hand. */
class FakeMotionSource(override val status: com.ps2gamepad.motion.MotionStatus = com.ps2gamepad.motion.MotionStatus(true, true)) : com.ps2gamepad.motion.MotionSource {
    var listener: ((com.ps2gamepad.motion.MotionSample) -> Unit)? = null
    var starts = 0; var stops = 0
    val running get() = listener != null
    override fun start(onSample: (com.ps2gamepad.motion.MotionSample) -> Unit): Boolean {
        if (!status.usable) return false
        listener = onSample; starts++; return true
    }
    override fun stop() { listener = null; stops++ }
}
