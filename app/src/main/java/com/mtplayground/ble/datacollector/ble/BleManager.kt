package com.mtplayground.ble.datacollector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.mtplayground.ble.datacollector.ble.model.DiscoveredDevice
import com.mtplayground.ble.datacollector.core.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanStartResult {
    Started,
    AlreadyScanning,
    BluetoothUnavailable,
    BluetoothDisabled,
    ScannerUnavailable,
    PermissionDenied,
    Failed,
}

enum class ConnectionLifecycleState {
    Disconnected,
    Connecting,
    Connected,
}

enum class ConnectResult {
    Started,
    AlreadyConnecting,
    BluetoothUnavailable,
    BluetoothDisabled,
    InvalidAddress,
    PermissionDenied,
    Failed,
}

class BleManager(
    context: Context,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val appContext = context.applicationContext
    private val devicesByAddress = LinkedHashMap<String, DiscoveredDevice>()
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private val _connectionState = MutableStateFlow(ConnectionLifecycleState.Disconnected)
    private var currentGatt: BluetoothGatt? = null

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    val connectionState: StateFlow<ConnectionLifecycleState> = _connectionState.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                _connectionState.value = ConnectionLifecycleState.Disconnected
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!requestMaximumMtu(gatt)) {
                        discoverServices(gatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt(gatt)
                    _connectionState.value = ConnectionLifecycleState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            _connectionState.value = ConnectionLifecycleState.Connected
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(): ScanStartResult {
        if (_isScanning.value) {
            return ScanStartResult.AlreadyScanning
        }

        val adapter = bluetoothAdapter()
            ?: return ScanStartResult.BluetoothUnavailable

        val scanner = try {
            if (!adapter.isEnabled) {
                return ScanStartResult.BluetoothDisabled
            }
            adapter.bluetoothLeScanner
        } catch (_: SecurityException) {
            return ScanStartResult.PermissionDenied
        } ?: return ScanStartResult.ScannerUnavailable

        return try {
            scanner.startScan(
                emptyList<ScanFilter>(),
                scanSettings(),
                scanCallback,
            )
            _isScanning.value = true
            ScanStartResult.Started
        } catch (_: SecurityException) {
            ScanStartResult.PermissionDenied
        } catch (_: IllegalStateException) {
            ScanStartResult.Failed
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) {
            return
        }

        try {
            bluetoothAdapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Permission may have been revoked while scanning; scanning is no longer controlled here.
        } catch (_: IllegalStateException) {
            // Adapter state can change while stopping. Treat the scan as stopped for local state.
        } finally {
            _isScanning.value = false
        }
    }

    fun clearDiscoveredDevices() {
        devicesByAddress.clear()
        _discoveredDevices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String): ConnectResult {
        if (_connectionState.value == ConnectionLifecycleState.Connecting) {
            return ConnectResult.AlreadyConnecting
        }

        val adapter = bluetoothAdapter()
            ?: return ConnectResult.BluetoothUnavailable

        val device = try {
            if (!adapter.isEnabled) {
                return ConnectResult.BluetoothDisabled
            }
            adapter.getRemoteDevice(macAddress)
        } catch (_: IllegalArgumentException) {
            return ConnectResult.InvalidAddress
        } catch (_: SecurityException) {
            return ConnectResult.PermissionDenied
        }

        stopScan()
        closeGatt(currentGatt)
        _connectionState.value = ConnectionLifecycleState.Connecting

        val gatt = try {
            device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (_: SecurityException) {
            _connectionState.value = ConnectionLifecycleState.Disconnected
            return ConnectResult.PermissionDenied
        } catch (_: IllegalArgumentException) {
            _connectionState.value = ConnectionLifecycleState.Disconnected
            return ConnectResult.InvalidAddress
        }

        return if (gatt == null) {
            _connectionState.value = ConnectionLifecycleState.Disconnected
            ConnectResult.Failed
        } else {
            currentGatt = gatt
            ConnectResult.Started
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val gatt = currentGatt ?: run {
            _connectionState.value = ConnectionLifecycleState.Disconnected
            return
        }

        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            closeGatt(gatt)
            _connectionState.value = ConnectionLifecycleState.Disconnected
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val advertisedName = result.scanRecord?.deviceName
            ?.takeIf(::matchesNameFilter)
            ?: return
        val macAddress = result.device.address ?: return
        devicesByAddress[macAddress] = DiscoveredDevice(
            name = advertisedName,
            macAddress = macAddress,
            rssi = result.rssi,
            lastSeenMillis = nowMillis(),
        )
        _discoveredDevices.value = devicesByAddress.values
            .sortedWith(compareBy<DiscoveredDevice> { it.name.lowercase() }.thenBy { it.macAddress })
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private fun scanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

    @SuppressLint("MissingPermission")
    private fun requestMaximumMtu(gatt: BluetoothGatt): Boolean = try {
        gatt.requestMtu(Config.requestedMtu)
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    private fun discoverServices(gatt: BluetoothGatt) {
        val discoveryStarted = try {
            gatt.discoverServices()
        } catch (_: SecurityException) {
            false
        }

        if (!discoveryStarted) {
            _connectionState.value = ConnectionLifecycleState.Connected
        }
    }

    private fun closeGatt(gatt: BluetoothGatt?) {
        if (gatt == null) {
            return
        }

        try {
            gatt.close()
        } finally {
            if (currentGatt == gatt) {
                currentGatt = null
            }
        }
    }

    companion object {
        fun matchesNameFilter(
            advertisedName: String,
            prefixes: List<String> = Config.bleNameFilterPrefixes,
        ): Boolean = prefixes.any { prefix ->
            advertisedName.startsWith(prefix)
        }
    }
}
