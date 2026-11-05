package com.ps2gamepad.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice

/** Narrow listener so [HidDeviceManager] does not have to subclass the framework callback itself. */
interface HidCallbackListener {
    fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean)
    fun onConnectionStateChanged(device: BluetoothDevice, state: Int)
    fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int)
    fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray?)
    fun onSetProtocol(device: BluetoothDevice, protocol: Byte)
    fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray?)
    fun onVirtualCableUnplug(device: BluetoothDevice)
}

/** Forwards BluetoothHidDevice callbacks (delivered on the executor passed to registerApp). */
class HidDeviceCallback(private val listener: HidCallbackListener) : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) =
        listener.onAppStatusChanged(pluggedDevice, registered)

    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) =
        listener.onConnectionStateChanged(device, state)

    override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) =
        listener.onGetReport(device, type, id, bufferSize)

    override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray?) =
        listener.onSetReport(device, type, id, data)

    override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) =
        listener.onSetProtocol(device, protocol)

    override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray?) =
        listener.onInterruptData(device, reportId, data)

    override fun onVirtualCableUnplug(device: BluetoothDevice) =
        listener.onVirtualCableUnplug(device)
}
