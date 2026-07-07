package com.mtplayground.ble.datacollector.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayground.ble.datacollector.ble.BleManager
import com.mtplayground.ble.datacollector.ble.ConnectResult
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import com.mtplayground.ble.datacollector.format.PacketFormatter
import com.mtplayground.ble.datacollector.storage.FileLogger
import com.mtplayground.ble.datacollector.storage.FileLoggerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val deviceAddress: String? = null,
    val lifecycleState: ConnectionLifecycleState = ConnectionLifecycleState.Disconnected,
    val errorMessage: String? = null,
    val formattedRecords: List<String> = emptyList(),
    val recordingState: RecordingUiState = RecordingUiState(),
) {
    val isConnected: Boolean = lifecycleState == ConnectionLifecycleState.Connected
    val isConnecting: Boolean = lifecycleState == ConnectionLifecycleState.Connecting
    val canDisconnect: Boolean = lifecycleState != ConnectionLifecycleState.Disconnected
    val canRetry: Boolean = deviceAddress != null &&
        lifecycleState == ConnectionLifecycleState.Disconnected
    val recordCount: Int = formattedRecords.size
    val canStartRecording: Boolean = isConnected && !recordingState.isRecording
    val canStopRecording: Boolean = recordingState.isRecording
}

data class RecordingUiState(
    val isRecording: Boolean = false,
    val activeFileName: String? = null,
    val writtenRecordCount: Int = 0,
    val errorMessage: String? = null,
)

class ConnectionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bleManager = BleManager(application)
    private val fileLogger = FileLogger(application)
    private val fileLoggerLock = Any()
    private val targetDeviceAddress = MutableStateFlow<String?>(null)
    private val formattedRecords = MutableStateFlow<List<String>>(emptyList())
    private val recordingState = MutableStateFlow(RecordingUiState())
    private var lastPacketDeviceName: String? = null
    private var autoStartConsumedForConnection = false

    val uiState: StateFlow<ConnectionUiState> = combine(
        targetDeviceAddress,
        bleManager.connectionState,
        bleManager.connectionError,
        formattedRecords,
        recordingState,
    ) { deviceAddress, lifecycleState, errorMessage, records, recording ->
        ConnectionUiState(
            deviceAddress = deviceAddress,
            lifecycleState = lifecycleState,
            errorMessage = errorMessage,
            formattedRecords = records,
            recordingState = recording,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ConnectionUiState(),
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.incomingPackets.collect { packet ->
                lastPacketDeviceName = packet.deviceName
                val formattedRecord = PacketFormatter.format(packet)
                formattedRecords.value += formattedRecord
                appendRecordIfRecording(formattedRecord)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            bleManager.connectionState.collect { lifecycleState ->
                when (lifecycleState) {
                    ConnectionLifecycleState.Connected -> {
                        if (!autoStartConsumedForConnection) {
                            autoStartConsumedForConnection = true
                            startRecording(resolveRecordingDeviceName())
                        }
                    }

                    ConnectionLifecycleState.Disconnected -> {
                        autoStartConsumedForConnection = false
                        stopRecording()
                    }

                    ConnectionLifecycleState.Connecting -> Unit
                }
            }
        }
    }

    fun connect(deviceAddress: String) {
        targetDeviceAddress.value = deviceAddress
        bleManager.connect(deviceAddress)
    }

    fun disconnect() {
        stopRecording()
        bleManager.disconnect()
    }

    fun retry() {
        when (bleManager.reconnect()) {
            ConnectResult.Started,
            ConnectResult.AlreadyConnecting,
            -> Unit

            ConnectResult.BluetoothUnavailable,
            ConnectResult.BluetoothDisabled,
            ConnectResult.InvalidAddress,
            ConnectResult.PermissionDenied,
            ConnectResult.Failed,
            -> Unit
        }
    }

    fun clearError() {
        bleManager.clearConnectionError()
    }

    fun startRecording() {
        autoStartConsumedForConnection = true
        startRecording(resolveRecordingDeviceName())
    }

    fun stopRecording(): FileLoggerResult {
        val result = synchronized(fileLoggerLock) {
            fileLogger.stop()
        }

        recordingState.value = when (result) {
            FileLoggerResult.Success -> RecordingUiState()
            is FileLoggerResult.Failure -> recordingState.value.copy(
                isRecording = false,
                activeFileName = null,
                errorMessage = result.message,
            )
        }
        return result
    }

    override fun onCleared() {
        stopRecording()
        bleManager.disconnect()
        super.onCleared()
    }

    private fun startRecording(deviceName: String): FileLoggerResult {
        val result = synchronized(fileLoggerLock) {
            fileLogger.start(deviceName)
        }

        recordingState.value = when (result) {
            FileLoggerResult.Success -> RecordingUiState(
                isRecording = true,
                activeFileName = fileLogger.currentSession?.displayName,
            )

            is FileLoggerResult.Failure -> RecordingUiState(
                isRecording = false,
                errorMessage = result.message,
            )
        }
        return result
    }

    private fun appendRecordIfRecording(formattedRecord: String) {
        if (!recordingState.value.isRecording) {
            return
        }

        val result = synchronized(fileLoggerLock) {
            fileLogger.appendRecord(formattedRecord)
        }

        recordingState.value = when (result) {
            FileLoggerResult.Success -> recordingState.value.copy(
                writtenRecordCount = recordingState.value.writtenRecordCount + 1,
                errorMessage = null,
            )

            is FileLoggerResult.Failure -> recordingState.value.copy(
                isRecording = false,
                activeFileName = null,
                errorMessage = result.message,
            )
        }
    }

    private fun resolveRecordingDeviceName(): String =
        lastPacketDeviceName
            ?: targetDeviceAddress.value
            ?: "device"
}
