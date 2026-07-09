package com.mtplayground.ble.datacollector.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayground.ble.datacollector.ble.BleManager
import com.mtplayground.ble.datacollector.ble.CommandWriteResult
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import com.mtplayground.ble.datacollector.ble.model.BlePacket
import com.mtplayground.ble.datacollector.ble.model.DeviceConnectionState
import com.mtplayground.ble.datacollector.ble.protocol.BleCommand
import com.mtplayground.ble.datacollector.ble.protocol.SensorInitState
import com.mtplayground.ble.datacollector.ble.protocol.SensorProtocol
import com.mtplayground.ble.datacollector.ble.protocol.SensorSide
import com.mtplayground.ble.datacollector.format.PacketFormatter
import com.mtplayground.ble.datacollector.storage.FileLogger
import com.mtplayground.ble.datacollector.storage.FileLoggerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ConnectionUiState(
    val devices: List<DeviceConnectionState> = emptyList(),
    val formattedRecords: List<String> = emptyList(),
    val recordingState: RecordingUiState = RecordingUiState(),
    val sensorControls: Map<String, DeviceSensorControlState> = emptyMap(),
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

    fun sensorControlFor(macAddress: String): DeviceSensorControlState =
        sensorControls[macAddress] ?: DeviceSensorControlState(macAddress = macAddress)
}

data class RecordingUiState(
    val isRecording: Boolean = false,
    val activeFileName: String? = null,
    val writtenRecordCount: Int = 0,
    val errorMessage: String? = null,
)

data class DeviceSensorControlState(
    val macAddress: String,
    val initializedSensors: Set<SensorInitState> = emptySet(),
    val selectedSide: SensorSide? = null,
    val isCollecting: Boolean = false,
    val isCommandInProgress: Boolean = false,
    val commandMessage: String? = null,
    val commandError: String? = null,
) {
    val isInitialized: Boolean = initializedSensors.containsAll(SensorInitState.entries)
}

class ConnectionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bleManager = BleManager.shared(application)
    private val fileLogger = FileLogger(application)
    private val fileLoggerLock = Any()
    private val formattedRecords = MutableStateFlow<List<String>>(emptyList())
    private val recordingState = MutableStateFlow(RecordingUiState())
    private val sensorControls = MutableStateFlow<Map<String, DeviceSensorControlState>>(emptyMap())

    val uiState: StateFlow<ConnectionUiState> = combine(
        bleManager.connections,
        formattedRecords,
        recordingState,
        sensorControls,
    ) { devices, records, recording, controls ->
        val sortedDevices = devices.sortedWith(
            compareBy<DeviceConnectionState> { it.deviceName.lowercase() }
                .thenBy { it.macAddress },
        )
        ConnectionUiState(
            devices = sortedDevices,
            formattedRecords = records,
            recordingState = recording,
            sensorControls = sortedDevices.associate { device ->
                device.macAddress to (controls[device.macAddress]
                    ?: DeviceSensorControlState(macAddress = device.macAddress))
            },
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
                val connectedAddresses = devices
                    .filter { device -> device.lifecycleState == ConnectionLifecycleState.Connected }
                    .map(DeviceConnectionState::macAddress)
                    .toSet()
                sensorControls.update { controls ->
                    controls.mapValues { (macAddress, control) ->
                        if (macAddress in connectedAddresses) {
                            control
                        } else {
                            control.copy(
                                isCollecting = false,
                                isCommandInProgress = false,
                            )
                        }
                    }
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
        sensorControls.update { controls ->
            controls.mapValues { (_, control) -> control.copy(commandError = null) }
        }
    }

    fun initializeSensors(macAddress: String) {
        launchCommand(macAddress) {
            updateSensorControl(macAddress) { control ->
                control.copy(
                    initializedSensors = emptySet(),
                    commandMessage = "正在初始化传感器",
                    commandError = null,
                )
            }

            for (sensor in SensorInitializationOrder) {
                val command = SensorProtocol.buildInitializeCommand(sensor)
                if (!writeCommandOrSetError(macAddress, command)) {
                    return@launchCommand
                }
                awaitCommandResponse(
                    macAddress = macAddress,
                    errorMessage = "${sensor.displayName()} 初始化超时",
                ) { packet ->
                    SensorProtocol.matchesInitializationSuccess(sensor, packet.rawBytes)
                }
                updateSensorControl(macAddress) { control ->
                    control.copy(
                        initializedSensors = control.initializedSensors + sensor,
                        commandMessage = "${sensor.displayName()} 初始化成功",
                        commandError = null,
                    )
                }
            }

            updateSensorControl(macAddress) { control ->
                control.copy(
                    commandMessage = "传感器初始化完成",
                    commandError = null,
                )
            }
        }
    }

    fun setDataSource(macAddress: String, side: SensorSide) {
        launchCommand(macAddress) {
            val command = SensorProtocol.buildSetDataSourceCommand(side)
            if (!writeCommandOrSetError(macAddress, command)) {
                return@launchCommand
            }
            awaitCommandResponse(
                macAddress = macAddress,
                errorMessage = "设置数据源超时",
            ) { packet ->
                SensorProtocol.matchesDataSourceSet(side, packet.rawBytes)
            }
            updateSensorControl(macAddress) { control ->
                control.copy(
                    selectedSide = side,
                    commandMessage = "数据源已设置为${side.displayName()}",
                    commandError = null,
                )
            }
        }
    }

    fun startCollecting(macAddress: String) {
        launchCommand(macAddress) {
            val command = SensorProtocol.buildStartCollectionCommand()
            if (!writeCommandOrSetError(macAddress, command)) {
                return@launchCommand
            }
            val initializationFailure = withTimeoutOrNull(CommandTimeoutMillis) {
                bleManager.incomingPackets.first { packet ->
                    packet.macAddress == macAddress &&
                        SensorProtocol.matchesSensorInitializationFailure(packet.rawBytes)
                }
            }
            if (initializationFailure != null) {
                updateSensorControl(macAddress) { control ->
                    control.copy(
                        isCollecting = false,
                        commandMessage = null,
                        commandError = SensorInitializationFailureMessage,
                    )
                }
                return@launchCommand
            }
            updateSensorControl(macAddress) { control ->
                control.copy(
                    isCollecting = true,
                    commandMessage = "开始采集",
                    commandError = null,
                )
            }
        }
    }

    fun stopCollecting(macAddress: String) {
        launchCommand(macAddress) {
            val command = SensorProtocol.buildStopCollectionCommand()
            if (!writeCommandOrSetError(macAddress, command)) {
                return@launchCommand
            }
            updateSensorControl(macAddress) { control ->
                control.copy(
                    isCollecting = false,
                    commandMessage = "停止采集",
                    commandError = null,
                )
            }
        }
    }

    fun querySensorInitStatus(macAddress: String, side: SensorSide) {
        launchCommand(macAddress) {
            val command = SensorProtocol.buildQuerySensorInitStatusCommand(side)
            if (!writeCommandOrSetError(macAddress, command)) {
                return@launchCommand
            }
            updateSensorControl(macAddress) { control ->
                control.copy(
                    commandMessage = "已查询${side.displayName()}初始化状态",
                    commandError = null,
                )
            }
        }
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

    private fun launchCommand(macAddress: String, block: suspend () -> Unit) {
        val started = markCommandStarted(macAddress)
        if (!started) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (exception: CommandFlowException) {
                updateSensorControl(macAddress) { control ->
                    control.copy(commandError = exception.message)
                }
            } catch (_: Exception) {
                updateSensorControl(macAddress) { control ->
                    control.copy(commandError = "Command failed.")
                }
            } finally {
                updateSensorControl(macAddress) { control ->
                    control.copy(isCommandInProgress = false)
                }
            }
        }
    }

    private fun markCommandStarted(macAddress: String): Boolean {
        val current = sensorControls.value[macAddress]
        if (current?.isCommandInProgress == true) {
            updateSensorControl(macAddress) { control ->
                control.copy(commandError = "Command already in progress.")
            }
            return false
        }
        updateSensorControl(macAddress) { control ->
            control.copy(
                isCommandInProgress = true,
                commandError = null,
            )
        }
        return true
    }

    private suspend fun writeCommandOrSetError(macAddress: String, command: BleCommand): Boolean {
        return when (val result = bleManager.writeCommand(macAddress, command.copyBytes())) {
            CommandWriteResult.Started -> true
            else -> {
                updateSensorControl(macAddress) { control ->
                    control.copy(commandError = result.toDisplayMessage())
                }
                false
            }
        }
    }

    private suspend fun awaitCommandResponse(
        macAddress: String,
        errorMessage: String,
        matches: (BlePacket) -> Boolean,
    ): BlePacket = withTimeoutOrNull(CommandTimeoutMillis) {
        bleManager.incomingPackets.first { packet ->
            packet.macAddress == macAddress && matches(packet)
        }
    } ?: throw CommandFlowException(errorMessage)

    private fun updateSensorControl(
        macAddress: String,
        update: (DeviceSensorControlState) -> DeviceSensorControlState,
    ) {
        sensorControls.update { controls ->
            val current = controls[macAddress] ?: DeviceSensorControlState(macAddress = macAddress)
            controls + (macAddress to update(current))
        }
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

    private fun SensorInitState.displayName(): String = when (this) {
        SensorInitState.SixAxis -> "六轴"
        SensorInitState.Magnetometer -> "地磁"
        SensorInitState.Barometer -> "气压计"
    }

    private fun SensorSide.displayName(): String = when (this) {
        SensorSide.Left -> "左脚"
        SensorSide.Right -> "右脚"
    }

    private class CommandFlowException(message: String) : Exception(message)

    private fun CommandWriteResult.toDisplayMessage(): String = when (this) {
        CommandWriteResult.Started -> "Command started."
        CommandWriteResult.AlreadyInProgress -> "Command already in progress."
        CommandWriteResult.DeviceNotConnected -> "Device is not connected."
        CommandWriteResult.NoWritableCharacteristic -> "No writable characteristic is available."
        CommandWriteResult.PermissionDenied -> "Bluetooth write permission is required."
        CommandWriteResult.Failed -> "Unable to write command."
    }

    private companion object {
        const val CommandTimeoutMillis: Long = 4_000
        const val SensorInitializationFailureMessage: String = "有传感器未初始化成功"
        val SensorInitializationOrder: List<SensorInitState> = listOf(
            SensorInitState.SixAxis,
            SensorInitState.Magnetometer,
            SensorInitState.Barometer,
        )
    }
}
