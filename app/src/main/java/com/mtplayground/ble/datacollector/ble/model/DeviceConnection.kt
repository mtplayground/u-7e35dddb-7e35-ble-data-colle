package com.mtplayground.ble.datacollector.ble.model

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState

data class DeviceConnectionState(
    val deviceName: String,
    val macAddress: String,
    val lifecycleState: ConnectionLifecycleState,
    val errorMessage: String?,
    val lastCommandWriteStatus: Int? = null,
)

internal data class DeviceConnection(
    val deviceName: String,
    val macAddress: String,
    var gatt: BluetoothGatt?,
    var lifecycleState: ConnectionLifecycleState,
    var errorMessage: String?,
    var notifiableCharacteristic: BluetoothGattCharacteristic? = null,
    var writableCharacteristic: BluetoothGattCharacteristic? = null,
    var isCommandWriteInProgress: Boolean = false,
    var lastCommandWriteStatus: Int? = null,
    var manualDisconnectRequested: Boolean = false,
) {
    fun toState(): DeviceConnectionState =
        DeviceConnectionState(
            deviceName = deviceName,
            macAddress = macAddress,
            lifecycleState = lifecycleState,
            errorMessage = errorMessage,
            lastCommandWriteStatus = lastCommandWriteStatus,
        )
}
