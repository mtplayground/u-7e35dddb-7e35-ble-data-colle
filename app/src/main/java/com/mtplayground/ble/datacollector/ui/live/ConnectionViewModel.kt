package com.mtplayground.ble.datacollector.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayground.ble.datacollector.ble.BleManager
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import com.mtplayground.ble.datacollector.ble.model.DeviceConnectionState
import com.mtplayground.ble.datacollector.format.PacketFormatter
import com.mtplayground.ble.datacollector.storage.FileLogger
import com.mtplayground.ble.datacollector.storage.FileLoggerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val devices: List<DeviceConnectionState> = emptyList(),
    val formattedRecords: List<String> = emptyList(),
    val recordingState: RecordingUiState = RecordingUiState(),
) {
    val connectedDevices: List<DeviceConnectionState> = devices.filter { device ->
        device.lifecycleState == ConnectionLifecycleState.Connected
    }
    val hasConnectedDevices: Boolean = connectedDevices.isNotEmpty()
    val hasDisconnectableDevices: Boolean = devices.any { device ->
        device.lifecycleState != ConnectionLifecycleState.Disconnected
    }
    val recordCount: Int = formattedRecords.size
    val canStartRecording: Boolean = hasConnectedDevices && !recordingState.isRecording
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
    private val bleManager = BleManager.shared(application)
    private val fileLogger = FileLogger(application)
    private val fileLoggerLock = Any()
    private val formattedRecords = MutableStateFlow<List<String>>(emptyList())
    private val recordingState = MutableStateFlow(RecordingUiState())

    val uiState: StateFlow<ConnectionUiState> = combine(
        bleManager.connections,
        formattedRecords,
        recordingState,
    ) { devices, records, recording ->
        ConnectionUiState(
            devices = devices.sortedWith(
                compareBy<DeviceConnectionState> { it.deviceName.lowercase() }
                    .thenBy { it.macAddress },
            ),
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
                val formattedRecord = PacketFormatter.format(packet)
                formattedRecords.update { records -> records + formattedRecord }
                appendRecordIfRecording(formattedRecord)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            bleManager.connections.collect { devices ->
                val anyConnected = devices.any { device ->
                    device.lifecycleState == ConnectionLifecycleState.Connected
                }
                if (!anyConnected && recordingState.value.isRecording) {
                    stopRecording()
                }
            }
        }
    }

    fun disconnectDevice(macAddress: String) {
        bleManager.disconnect(macAddress)
    }

    fun disconnectAll() {
        stopRecording()
        bleManager.disconnectAll()
    }

    fun clearErrors() {
        bleManager.clearConnectionError()
        recordingState.update { state -> state.copy(errorMessage = null) }
    }

    fun startRecording(): FileLoggerResult {
        if (!uiState.value.hasConnectedDevices) {
            recordingState.update { state ->
                state.copy(errorMessage = "Connect at least one device before recording.")
            }
            return FileLoggerResult.Failure("Connect at least one device before recording.")
        }

        val result = synchronized(fileLoggerLock) {
            fileLogger.startAggregatedSession()
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
        super.onCleared()
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
}
