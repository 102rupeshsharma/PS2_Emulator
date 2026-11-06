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
    val s = MutableStateFlow(HidState())
    override val state: StateFlow<HidState> = s
    override val stats: StateFlow<SenderStats> = MutableStateFlow(SenderStats())
    var registerCalls = 0; var unregisterCalls = 0; var bluetoothOffCalls = 0; var closed = false
    val connects = mutableListOf<String>()
    val sent = mutableListOf<ByteArray>()
    fun emit(e: HidEvent) { s.value = HidStateReducer.reduce(s.value, e) }
    var lastAutoConnect = true
    var lastComposite = false
    /** PC Mode reports (report id, bytes), only accepted when registered as composite and connected. */
    val inputReports = mutableListOf<Pair<Int, ByteArray>>()
    override fun register(autoConnectPlugged: Boolean, composite: Boolean) {
        registerCalls++; lastAutoConnect = autoConnectPlugged; lastComposite = composite; emit(HidEvent.RegisterRequested)
    }
    override fun sendInputReport(reportId: Int, report: ByteArray, eventUptimeMs: Long): Boolean {
        if (reportId == 1) return sendReport(report, eventUptimeMs)
        if (!lastComposite || !s.value.isConnected) return false
        inputReports += reportId to report; return true
    }
    fun keyboardReports() = inputReports.filter { it.first == 2 }.map { it.second }
    fun mouseReports() = inputReports.filter { it.first == 3 }.map { it.second }
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
