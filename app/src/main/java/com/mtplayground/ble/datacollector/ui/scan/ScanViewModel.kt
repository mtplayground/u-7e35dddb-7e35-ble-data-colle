package com.mtplayground.ble.datacollector.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayground.ble.datacollector.ble.BleManager
import com.mtplayground.ble.datacollector.ble.ScanStartResult
import com.mtplayground.ble.datacollector.ble.model.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ScanUiState(
    val devices: List<DiscoveredDevice> = emptyList(),
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
        statusMessage,
    ) { devices, isScanning, message ->
        ScanUiState(
            devices = devices,
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

    fun selectDevice(device: DiscoveredDevice) {
        bleManager.stopScan()
        statusMessage.value = "Connecting to ${device.name}."
    }

    override fun onCleared() {
        bleManager.stopScan()
        super.onCleared()
    }
}
