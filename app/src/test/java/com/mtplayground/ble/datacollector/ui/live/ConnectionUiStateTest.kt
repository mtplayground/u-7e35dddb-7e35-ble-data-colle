package com.mtplayground.ble.datacollector.ui.live

import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import com.mtplayground.ble.datacollector.ble.model.DeviceConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionUiStateTest {
    @Test
    fun connectionUiStateTracksMultipleDevicesIndependently() {
        val connectedDevice = DeviceConnectionState(
            deviceName = "x_skiing",
            macAddress = "3F:89:E5:1E:2A:EF",
            lifecycleState = ConnectionLifecycleState.Connected,
            errorMessage = null,
        )
        val connectingDevice = DeviceConnectionState(
            deviceName = "CM-1001",
            macAddress = "00:11:22:33:44:55",
            lifecycleState = ConnectionLifecycleState.Connecting,
            errorMessage = null,
        )
        val erroredDevice = DeviceConnectionState(
            deviceName = "CM-error",
            macAddress = "AA:BB:CC:DD:EE:FF",
            lifecycleState = ConnectionLifecycleState.Disconnected,
            errorMessage = "GATT connection failed with status 133.",
        )

        val state = ConnectionUiState(
            devices = listOf(connectedDevice, connectingDevice, erroredDevice),
            formattedRecords = listOf("record-1", "record-2"),
        )

        assertEquals(listOf(connectedDevice), state.connectedDevices)
        assertTrue(state.hasConnectedDevices)
        assertTrue(state.hasDisconnectableDevices)
        assertEquals(2, state.recordCount)
        assertTrue(state.canStartRecording)
        assertFalse(state.canStopRecording)
        assertEquals(ConnectionLifecycleState.Connecting, state.devices[1].lifecycleState)
        assertEquals("GATT connection failed with status 133.", state.devices[2].errorMessage)
    }

    @Test
    fun recordingStateControlsStartAndStopAvailability() {
        val connectedDevice = DeviceConnectionState(
            deviceName = "x_skiing",
            macAddress = "3F:89:E5:1E:2A:EF",
            lifecycleState = ConnectionLifecycleState.Connected,
            errorMessage = null,
        )

        val idleState = ConnectionUiState(devices = listOf(connectedDevice))
        val recordingState = ConnectionUiState(
            devices = listOf(connectedDevice),
            recordingState = RecordingUiState(
                isRecording = true,
                activeFileName = "ble-session_20260707_142530.txt",
                writtenRecordCount = 4,
            ),
        )
        val disconnectedState = ConnectionUiState(
            devices = listOf(
                connectedDevice.copy(lifecycleState = ConnectionLifecycleState.Disconnected),
            ),
        )

        assertTrue(idleState.canStartRecording)
        assertFalse(idleState.canStopRecording)
        assertFalse(recordingState.canStartRecording)
        assertTrue(recordingState.canStopRecording)
        assertFalse(disconnectedState.canStartRecording)
        assertFalse(disconnectedState.hasConnectedDevices)
    }
}
