package com.mtplayground.ble.datacollector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.mtplayground.ble.datacollector.ble.model.BlePacket
import com.mtplayground.ble.datacollector.ble.model.DiscoveredDevice
import com.mtplayground.ble.datacollector.core.Config
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val _connectionError = MutableStateFlow<String?>(null)
    private val _incomingPackets = MutableSharedFlow<BlePacket>(extraBufferCapacity = 64)
    private var currentGatt: BluetoothGatt? = null
    private var connectedDeviceName: String? = null
    private var connectedMacAddress: String? = null
    private var lastRequestedMacAddress: String? = null
    private var manualDisconnectRequested: Boolean = false

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    val connectionState: StateFlow<ConnectionLifecycleState> = _connectionState.asStateFlow()
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()
    val incomingPackets: SharedFlow<BlePacket> = _incomingPackets.asSharedFlow()

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
                markDisconnected(gatt, "GATT connection failed with status $status.")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionError.value = null
                    if (!requestMaximumMtu(gatt)) {
                        discoverServices(gatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val message = if (manualDisconnectRequested) {
                        null
                    } else {
                        "Device disconnected."
                    }
                    markDisconnected(gatt, message)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                markDisconnected(gatt, "Service discovery failed with status $status.")
                return
            }

            subscribeToFirstNotifiableCharacteristic(gatt)
            _connectionState.value = ConnectionLifecycleState.Connected
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            emitPacket(gatt, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            emitPacket(gatt, characteristic.value ?: ByteArray(0))
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

        lastRequestedMacAddress = macAddress
        _connectionError.value = null
        manualDisconnectRequested = false

        val adapter = bluetoothAdapter()
            ?: return connectionFailure(
                result = ConnectResult.BluetoothUnavailable,
                message = "Bluetooth is not available on this device.",
            )

        val device = try {
            if (!adapter.isEnabled) {
                return connectionFailure(
                    result = ConnectResult.BluetoothDisabled,
                    message = "Bluetooth is disabled.",
                )
            }
            adapter.getRemoteDevice(macAddress)
        } catch (_: IllegalArgumentException) {
            return connectionFailure(
                result = ConnectResult.InvalidAddress,
                message = "Device address is invalid.",
            )
        } catch (_: SecurityException) {
            return connectionFailure(
                result = ConnectResult.PermissionDenied,
                message = "Bluetooth connect permission is required.",
            )
        }

        stopScan()
        closeGatt(currentGatt)
        connectedMacAddress = macAddress
        connectedDeviceName = devicesByAddress[macAddress]?.name ?: bluetoothDeviceName(device, macAddress)
        _connectionState.value = ConnectionLifecycleState.Connecting

        val gatt = try {
            device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (_: SecurityException) {
            return connectionFailure(
                result = ConnectResult.PermissionDenied,
                message = "Bluetooth connect permission is required.",
            )
        } catch (_: IllegalArgumentException) {
            return connectionFailure(
                result = ConnectResult.InvalidAddress,
                message = "Device address is invalid.",
            )
        }

        return if (gatt == null) {
            connectionFailure(
                result = ConnectResult.Failed,
                message = "Unable to open a GATT connection.",
            )
        } else {
            currentGatt = gatt
            ConnectResult.Started
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val gatt = currentGatt ?: run {
            _connectionState.value = ConnectionLifecycleState.Disconnected
            _connectionError.value = null
            return
        }

        manualDisconnectRequested = true
        _connectionError.value = null
        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            markDisconnected(gatt, "Bluetooth connect permission was revoked.")
        }
    }

    fun reconnect(): ConnectResult {
        val macAddress = lastRequestedMacAddress
            ?: return connectionFailure(
                result = ConnectResult.InvalidAddress,
                message = "No previous device is available to reconnect.",
            )
        return connect(macAddress)
    }

    fun clearConnectionError() {
        _connectionError.value = null
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
    private fun bluetoothDeviceName(device: BluetoothDevice, fallback: String): String = try {
        device.name ?: fallback
    } catch (_: SecurityException) {
        fallback
    }

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
            markDisconnected(gatt, "Service discovery could not be started.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToFirstNotifiableCharacteristic(gatt: BluetoothGatt) {
        val characteristic = gatt.services
            .asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { characteristic -> characteristic.supportsNotifications() }
            ?: return

        val descriptor = characteristic.getDescriptor(ClientCharacteristicConfigUuid) ?: return
        val notificationEnabled = try {
            gatt.setCharacteristicNotification(characteristic, true)
        } catch (_: SecurityException) {
            false
        }

        if (!notificationEnabled) {
            return
        }

        writeNotificationDescriptor(
            gatt = gatt,
            descriptor = descriptor,
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
        )
    }

    private fun BluetoothGattCharacteristic.supportsNotifications(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeNotificationDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    } catch (_: SecurityException) {
        false
    }

    private fun emitPacket(gatt: BluetoothGatt, rawBytes: ByteArray) {
        val macAddress = connectedMacAddress ?: gatt.device.address
        val deviceName = connectedDeviceName ?: macAddress
        _incomingPackets.tryEmit(
            BlePacket(
                deviceName = deviceName,
                macAddress = macAddress,
                rawBytes = rawBytes.copyOf(),
                receivedAtMillis = nowMillis(),
            ),
        )
    }

    private fun connectionFailure(result: ConnectResult, message: String): ConnectResult {
        closeGatt(currentGatt)
        connectedDeviceName = null
        connectedMacAddress = null
        manualDisconnectRequested = false
        _connectionState.value = ConnectionLifecycleState.Disconnected
        _connectionError.value = message
        return result
    }

    private fun markDisconnected(gatt: BluetoothGatt?, message: String?) {
        closeGatt(gatt)
        manualDisconnectRequested = false
        _connectionState.value = ConnectionLifecycleState.Disconnected
        _connectionError.value = message
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
                connectedDeviceName = null
                connectedMacAddress = null
            }
        }
    }

    companion object {
        private val ClientCharacteristicConfigUuid: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun matchesNameFilter(
            advertisedName: String,
            prefixes: List<String> = Config.bleNameFilterPrefixes,
        ): Boolean = prefixes.any { prefix ->
            advertisedName.startsWith(prefix)
        }
    }
}
