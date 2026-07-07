package com.mtplayground.ble.datacollector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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

class BleManager(
    context: Context,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val appContext = context.applicationContext
    private val devicesByAddress = LinkedHashMap<String, DiscoveredDevice>()
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private val _isScanning = MutableStateFlow(false)

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

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

    companion object {
        fun matchesNameFilter(
            advertisedName: String,
            prefixes: List<String> = Config.bleNameFilterPrefixes,
        ): Boolean = prefixes.any { prefix ->
            advertisedName.startsWith(prefix)
        }
    }
}
