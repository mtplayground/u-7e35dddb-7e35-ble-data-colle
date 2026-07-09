package com.mtplayground.ble.datacollector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.mtplayground.ble.datacollector.ble.model.BlePacket
import com.mtplayground.ble.datacollector.ble.model.DeviceConnection
import com.mtplayground.ble.datacollector.ble.model.DeviceConnectionState
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

enum class CommandWriteResult {
    Started,
    AlreadyInProgress,
    DeviceNotConnected,
    NoWritableCharacteristic,
    PermissionDenied,
    Failed,
}

class BleManager(
    context: Context,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val appContext = context.applicationContext
    private val devicesByAddress = LinkedHashMap<String, DiscoveredDevice>()
    private val connectionsByAddress = LinkedHashMap<String, DeviceConnection>()
    private val connectionLock = Any()
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private val _connections = MutableStateFlow<List<DeviceConnectionState>>(emptyList())
    private val _connectionState = MutableStateFlow(ConnectionLifecycleState.Disconnected)
    private val _connectionError = MutableStateFlow<String?>(null)
    private val _incomingPackets = MutableSharedFlow<BlePacket>(extraBufferCapacity = 64)
    private var lastRequestedMacAddress: String? = null

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    val connections: StateFlow<List<DeviceConnectionState>> = _connections.asStateFlow()

    // Retained for the current single-device UI. New multi-device screens should observe connections.
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
                markDisconnected(gatt, gattFailureMessage(status))
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateConnection(gatt) { connection ->
                        connection.lifecycleState = ConnectionLifecycleState.Connecting
                        connection.errorMessage = null
                    }
                    if (!requestMaximumMtu(gatt)) {
                        discoverServices(gatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val connection = connectionForGatt(gatt)
                    val message = if (connection?.manualDisconnectRequested == true) {
                        null
                    } else if (isBluetoothDisabled()) {
                        "Bluetooth was turned off. Enable Bluetooth and retry."
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

            updateConnection(gatt) { connection ->
                connection.writableCharacteristic = firstWritableCharacteristic(gatt)
            }
            val subscriptionResult = subscribeToFirstNotifiableCharacteristic(gatt)
            if (subscriptionResult == null) {
                updateConnection(gatt) { connection ->
                    connection.lifecycleState = ConnectionLifecycleState.Connected
                    connection.errorMessage = null
                }
            } else {
                markDisconnected(gatt, subscriptionResult)
            }
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

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            updateConnection(gatt) { connection ->
                connection.isCommandWriteInProgress = false
                connection.lastCommandWriteStatus = status
                connection.errorMessage = if (status == BluetoothGatt.GATT_SUCCESS) {
                    null
                } else {
                    "Command write failed with status $status."
                }
            }
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
        synchronized(connectionLock) {
            val existingConnection = connectionsByAddress[macAddress]
            if (existingConnection?.lifecycleState == ConnectionLifecycleState.Connecting ||
                existingConnection?.lifecycleState == ConnectionLifecycleState.Connected
            ) {
                return ConnectResult.AlreadyConnecting
            }
        }

        lastRequestedMacAddress = macAddress

        val adapter = bluetoothAdapter()
            ?: return connectionFailure(
                macAddress = macAddress,
                result = ConnectResult.BluetoothUnavailable,
                message = "Bluetooth is not available on this device.",
            )

        val device = try {
            if (!adapter.isEnabled) {
                return connectionFailure(
                    macAddress = macAddress,
                    result = ConnectResult.BluetoothDisabled,
                    message = "Bluetooth is disabled.",
                )
            }
            adapter.getRemoteDevice(macAddress)
        } catch (_: IllegalArgumentException) {
            return connectionFailure(
                macAddress = macAddress,
                result = ConnectResult.InvalidAddress,
                message = "Device address is invalid.",
            )
        } catch (_: SecurityException) {
            return connectionFailure(
                macAddress = macAddress,
                result = ConnectResult.PermissionDenied,
                message = "Bluetooth connect permission is required.",
            )
        }

        val deviceName = devicesByAddress[macAddress]?.name ?: bluetoothDeviceName(device, macAddress)
        closeGatt(connectionForAddress(macAddress)?.gatt)
        setConnection(
            DeviceConnection(
                deviceName = deviceName,
                macAddress = macAddress,
                gatt = null,
                lifecycleState = ConnectionLifecycleState.Connecting,
                errorMessage = null,
            ),
        )

        val gatt = try {
            device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (_: SecurityException) {
            return connectionFailure(
                macAddress = macAddress,
                result = ConnectResult.PermissionDenied,
                message = "Bluetooth connect permission is required.",
            )
        } catch (_: IllegalArgumentException) {
            return connectionFailure(
                macAddress = macAddress,
                result = ConnectResult.InvalidAddress,
                message = "Device address is invalid.",
            )
        }

        return if (gatt == null) {
            connectionFailure(
                macAddress = macAddress,
                result = ConnectResult.Failed,
                message = "Unable to open a GATT connection.",
            )
        } else {
            updateConnection(macAddress) { connection ->
                connection.gatt = gatt
                connection.lifecycleState = ConnectionLifecycleState.Connecting
                connection.errorMessage = null
                connection.manualDisconnectRequested = false
            }
            ConnectResult.Started
        }
    }

    fun disconnect() {
        disconnectAll()
    }

    @SuppressLint("MissingPermission")
    fun disconnect(macAddress: String) {
        val gatt = synchronized(connectionLock) {
            val connection = connectionsByAddress[macAddress] ?: return
            connection.manualDisconnectRequested = true
            connection.errorMessage = null
            publishConnectionsLocked()
            connection.gatt
        } ?: run {
            updateConnection(macAddress) { connection ->
                connection.lifecycleState = ConnectionLifecycleState.Disconnected
                connection.errorMessage = null
                connection.manualDisconnectRequested = false
            }
            return
        }

        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            markDisconnected(gatt, "Bluetooth connect permission was revoked.")
        } catch (_: RuntimeException) {
            markDisconnected(gatt, "Unable to disconnect cleanly.")
        }
    }

    fun disconnectAll() {
        connections.value.map(DeviceConnectionState::macAddress).forEach(::disconnect)
    }

    fun reconnect(): ConnectResult {
        val macAddress = lastRequestedMacAddress
            ?: return connectionFailure(
                macAddress = "",
                result = ConnectResult.InvalidAddress,
                message = "No previous device is available to reconnect.",
            )
        return connect(macAddress)
    }

    fun clearConnectionError() {
        synchronized(connectionLock) {
            connectionsByAddress.values.forEach { connection ->
                connection.errorMessage = null
            }
            publishConnectionsLocked()
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCommand(macAddress: String, bytes: ByteArray): CommandWriteResult {
        val writeTarget = synchronized(connectionLock) {
            val connection = connectionsByAddress[macAddress]
                ?: return CommandWriteResult.DeviceNotConnected
            val gatt = connection.gatt
                ?: return CommandWriteResult.DeviceNotConnected
            val characteristic = connection.writableCharacteristic
                ?: return CommandWriteResult.NoWritableCharacteristic

            if (connection.lifecycleState != ConnectionLifecycleState.Connected) {
                return CommandWriteResult.DeviceNotConnected
            }
            if (connection.isCommandWriteInProgress) {
                return CommandWriteResult.AlreadyInProgress
            }

            connection.isCommandWriteInProgress = true
            connection.lastCommandWriteStatus = null
            connection.errorMessage = null
            publishConnectionsLocked()

            CommandWriteTarget(
                gatt = gatt,
                characteristic = characteristic,
                writeType = characteristic.preferredWriteType(),
                payload = bytes.copyOf(),
            )
        }

        val writeStarted = try {
            writeTarget.characteristic.writeType = writeTarget.writeType
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                writeTarget.gatt.writeCharacteristic(
                    writeTarget.characteristic,
                    writeTarget.payload,
                    writeTarget.writeType,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                writeTarget.characteristic.value = writeTarget.payload
                @Suppress("DEPRECATION")
                writeTarget.gatt.writeCharacteristic(writeTarget.characteristic)
            }
        } catch (_: SecurityException) {
            clearCommandWriteInProgress(macAddress)
            return CommandWriteResult.PermissionDenied
        } catch (_: RuntimeException) {
            clearCommandWriteInProgress(macAddress)
            return CommandWriteResult.Failed
        }

        return if (writeStarted) {
            CommandWriteResult.Started
        } else {
            clearCommandWriteInProgress(macAddress)
            CommandWriteResult.Failed
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

    private fun isBluetoothDisabled(): Boolean {
        val adapter = bluetoothAdapter() ?: return true
        return try {
            !adapter.isEnabled
        } catch (_: SecurityException) {
            true
        }
    }

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
    private fun subscribeToFirstNotifiableCharacteristic(gatt: BluetoothGatt): String? {
        val characteristic = gatt.services
            .asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { characteristic -> characteristic.supportsNotifications() }
            ?: return "No notifiable characteristic was found on this device."

        updateConnection(gatt) { connection ->
            connection.notifiableCharacteristic = characteristic
        }

        val descriptor = characteristic.getDescriptor(ClientCharacteristicConfigUuid)
            ?: return "The notifiable characteristic is missing its notification descriptor."
        val notificationEnabled = try {
            gatt.setCharacteristicNotification(characteristic, true)
        } catch (_: SecurityException) {
            false
        }

        if (!notificationEnabled) {
            return "Bluetooth notification permission was denied or notification setup failed."
        }

        val descriptorWriteStarted = writeNotificationDescriptor(
            gatt = gatt,
            descriptor = descriptor,
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
        )
        return if (descriptorWriteStarted) {
            null
        } else {
            "Unable to enable Bluetooth notifications on this device."
        }
    }

    private fun BluetoothGattCharacteristic.supportsNotifications(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

    private fun firstWritableCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? =
        gatt.services
            .asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { characteristic -> characteristic.supportsWrites() }

    private fun BluetoothGattCharacteristic.supportsWrites(): Boolean =
        (properties and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            )) != 0

    private fun BluetoothGattCharacteristic.preferredWriteType(): Int = if (
        (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
    ) {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    } else {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    }

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
        val macAddress = gatt.device.address
        val deviceName = connectionForAddress(macAddress)?.deviceName ?: macAddress
        _incomingPackets.tryEmit(
            BlePacket(
                deviceName = deviceName,
                macAddress = macAddress,
                rawBytes = rawBytes.copyOf(),
                receivedAtMillis = nowMillis(),
            ),
        )
    }

    private fun connectionFailure(macAddress: String, result: ConnectResult, message: String): ConnectResult {
        val existingGatt = connectionForAddress(macAddress)?.gatt
        closeGatt(existingGatt)
        val deviceName = devicesByAddress[macAddress]?.name ?: macAddress.ifBlank { "device" }
        setConnection(
            DeviceConnection(
                deviceName = deviceName,
                macAddress = macAddress,
                gatt = null,
                lifecycleState = ConnectionLifecycleState.Disconnected,
                errorMessage = message,
            ),
        )
        return result
    }

    private fun markDisconnected(gatt: BluetoothGatt?, message: String?) {
        if (gatt == null) {
            return
        }

        val macAddress = gatt.device.address
        closeGatt(gatt)
        updateConnection(macAddress) { connection ->
            connection.gatt = null
            connection.notifiableCharacteristic = null
            connection.writableCharacteristic = null
            connection.isCommandWriteInProgress = false
            connection.lifecycleState = ConnectionLifecycleState.Disconnected
            connection.errorMessage = message
            connection.manualDisconnectRequested = false
        }
    }

    private fun closeGatt(gatt: BluetoothGatt?) {
        if (gatt == null) {
            return
        }

        try {
            gatt.close()
        } catch (_: RuntimeException) {
            // Adapter state can change while the stack is tearing down; local state is cleared below.
        } finally {
            val macAddress = gatt.device.address
            synchronized(connectionLock) {
                val connection = connectionsByAddress[macAddress]
                if (connection?.gatt == gatt) {
                    connection.gatt = null
                    connection.notifiableCharacteristic = null
                    connection.writableCharacteristic = null
                    connection.isCommandWriteInProgress = false
                    publishConnectionsLocked()
                }
            }
        }
    }

    private fun connectionForGatt(gatt: BluetoothGatt): DeviceConnection? =
        synchronized(connectionLock) {
            connectionsByAddress[gatt.device.address]
        }

    private fun connectionForAddress(macAddress: String): DeviceConnection? =
        synchronized(connectionLock) {
            connectionsByAddress[macAddress]
        }

    private fun setConnection(connection: DeviceConnection) {
        synchronized(connectionLock) {
            connectionsByAddress[connection.macAddress] = connection
            publishConnectionsLocked()
        }
    }

    private fun updateConnection(gatt: BluetoothGatt, update: (DeviceConnection) -> Unit) {
        updateConnection(gatt.device.address, update)
    }

    private fun updateConnection(macAddress: String, update: (DeviceConnection) -> Unit) {
        synchronized(connectionLock) {
            val connection = connectionsByAddress[macAddress] ?: return
            update(connection)
            publishConnectionsLocked()
        }
    }

    private fun clearCommandWriteInProgress(macAddress: String) {
        updateConnection(macAddress) { connection ->
            connection.isCommandWriteInProgress = false
            connection.lastCommandWriteStatus = null
        }
    }

    private fun publishConnectionsLocked() {
        val snapshots = connectionsByAddress.values.map(DeviceConnection::toState)
        _connections.value = snapshots
        _connectionState.value = when {
            snapshots.any { it.lifecycleState == ConnectionLifecycleState.Connected } ->
                ConnectionLifecycleState.Connected

            snapshots.any { it.lifecycleState == ConnectionLifecycleState.Connecting } ->
                ConnectionLifecycleState.Connecting

            else -> ConnectionLifecycleState.Disconnected
        }
        _connectionError.value = snapshots.firstOrNull { it.errorMessage != null }?.errorMessage
    }

    private fun gattFailureMessage(status: Int): String = if (isBluetoothDisabled()) {
        "Bluetooth was turned off. Enable Bluetooth and retry."
    } else {
        "GATT connection failed with status $status."
    }

    private data class CommandWriteTarget(
        val gatt: BluetoothGatt,
        val characteristic: BluetoothGattCharacteristic,
        val writeType: Int,
        val payload: ByteArray,
    )

    companion object {
        @Volatile
        private var sharedInstance: BleManager? = null

        private val ClientCharacteristicConfigUuid: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun shared(context: Context): BleManager = sharedInstance ?: synchronized(this) {
            sharedInstance ?: BleManager(context.applicationContext).also { manager ->
                sharedInstance = manager
            }
        }

        fun matchesNameFilter(
            advertisedName: String,
            prefixes: List<String> = Config.bleNameFilterPrefixes,
        ): Boolean = prefixes.any { prefix ->
            advertisedName.startsWith(prefix)
        }
    }
}
