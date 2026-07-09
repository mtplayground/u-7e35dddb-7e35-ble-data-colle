package com.mtplayground.ble.datacollector.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayground.ble.datacollector.ble.BleManager
import com.mtplayground.ble.datacollector.ble.ConnectResult
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import com.mtplayground.ble.datacollector.ble.ScanStartResult
import com.mtplayground.ble.datacollector.ble.model.DeviceConnectionState
import com.mtplayground.ble.datacollector.ble.model.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ScanDeviceUiState(
    val device: DiscoveredDevice,
    val connectionState: ConnectionLifecycleState = ConnectionLifecycleState.Disconnected,
    val connectionError: String? = null,
) {
    val isConnecting: Boolean = connectionState == ConnectionLifecycleState.Connecting
    val isConnected: Boolean = connectionState == ConnectionLifecycleState.Connected
    val canConnect: Boolean = !isConnecting && !isConnected
    val canDisconnect: Boolean = isConnecting || isConnected
    val stateLabel: String = when {
        connectionError != null -> "错误"
        isConnected -> "已连接"
        isConnecting -> "连接中"
        else -> "未连接"
    }
}

data class ScanUiState(
    val devices: List<ScanDeviceUiState> = emptyList(),
    val isScanning: Boolean = false,
    val statusMessage: String? = null,
)

class ScanViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bleManager = BleManager(application)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ScanUiState> = combine(
        bleManager.discoveredDevices,
        bleManager.isScanning,
        bleManager.connections,
        statusMessage,
    ) { devices, isScanning, connections, message ->
        ScanUiState(
            devices = devices.toScanDeviceUiStates(connections),
            isScanning = isScanning,
            statusMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ScanUiState(),
    )

    fun startScan() {
        when (bleManager.startScan()) {
            ScanStartResult.Started,
            ScanStartResult.AlreadyScanning,
            -> statusMessage.value = null

            ScanStartResult.BluetoothUnavailable ->
                statusMessage.value = "Bluetooth is not available on this device."

            ScanStartResult.BluetoothDisabled ->
                statusMessage.value = "Enable Bluetooth before scanning."

            ScanStartResult.ScannerUnavailable ->
                statusMessage.value = "Bluetooth scanner is not available."

            ScanStartResult.PermissionDenied ->
                statusMessage.value = "Bluetooth scan permission is required."

            ScanStartResult.Failed ->
                statusMessage.value = "Unable to start Bluetooth scan."
        }
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun clearDevices() {
        bleManager.clearDiscoveredDevices()
    }

    fun connectDevice(device: DiscoveredDevice) {
        when (bleManager.connect(device.macAddress)) {
            ConnectResult.Started ->
                statusMessage.value = "Connecting to ${device.name}."

            ConnectResult.AlreadyConnecting ->
                statusMessage.value = null

            ConnectResult.BluetoothUnavailable ->
                statusMessage.value = "Bluetooth is not available on this device."

            ConnectResult.BluetoothDisabled ->
                statusMessage.value = "Enable Bluetooth before connecting."

            ConnectResult.InvalidAddress ->
                statusMessage.value = "Device address is invalid."

            ConnectResult.PermissionDenied ->
                statusMessage.value = "Bluetooth connect permission is required."

            ConnectResult.Failed ->
                statusMessage.value = "Unable to connect to ${device.name}."
        }
    }

    fun disconnectDevice(device: DiscoveredDevice) {
        bleManager.disconnect(device.macAddress)
        statusMessage.value = "Disconnecting from ${device.name}."
    }

    fun selectDevice(device: DiscoveredDevice) {
        statusMessage.value = "Opening ${device.name}."
    }

    override fun onCleared() {
        bleManager.stopScan()
        bleManager.disconnectAll()
        super.onCleared()
    }
}

private fun List<DiscoveredDevice>.toScanDeviceUiStates(
    connections: List<DeviceConnectionState>,
): List<ScanDeviceUiState> {
    val connectionsByAddress = connections.associateBy(DeviceConnectionState::macAddress)
    return map { device ->
        val connection = connectionsByAddress[device.macAddress]
        ScanDeviceUiState(
            device = device,
            connectionState = connection?.lifecycleState ?: ConnectionLifecycleState.Disconnected,
            connectionError = connection?.errorMessage,
        )
    }
}
