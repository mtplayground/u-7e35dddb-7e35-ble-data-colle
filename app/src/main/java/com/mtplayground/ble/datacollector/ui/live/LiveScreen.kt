package com.mtplayground.ble.datacollector.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import com.mtplayground.ble.datacollector.ble.model.DeviceConnectionState
import com.mtplayground.ble.datacollector.ui.theme.BleDataCollectorTheme

@Suppress("UNUSED_PARAMETER")
@Composable
fun LiveScreen(
    deviceName: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connectionViewModel: ConnectionViewModel = viewModel(),
) {
    val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()

    LiveScreenContent(
        uiState = connectionState,
        onDisconnectDevice = connectionViewModel::disconnectDevice,
        onDisconnectAll = connectionViewModel::disconnectAll,
        onStartRecording = { connectionViewModel.startRecording() },
        onStopRecording = { connectionViewModel.stopRecording() },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun LiveScreenContent(
    uiState: ConnectionUiState,
    onDisconnectDevice: (String) -> Unit,
    onDisconnectAll: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Live",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Connected devices: ${uiState.connectedDevices.size}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Records: ${uiState.recordCount}",
            style = MaterialTheme.typography.bodyMedium,
        )
        RecordingStatus(
            uiState = uiState,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
        )
        DeviceConnectionList(
            devices = uiState.devices,
            onDisconnectDevice = onDisconnectDevice,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDisconnectAll,
                enabled = uiState.hasDisconnectableDevices,
            ) {
                Text(text = "Disconnect All")
            }
            Button(onClick = onBack) {
                Text(text = "Back")
            }
        }
        LiveRecordList(
            records = uiState.formattedRecords,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecordingStatus(
    uiState: ConnectionUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (uiState.recordingState.isRecording) "Recording: Active" else "Recording: Stopped",
            style = MaterialTheme.typography.bodyMedium,
        )
        uiState.recordingState.activeFileName?.let { fileName ->
            Text(
                text = "File: $fileName",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = "Written: ${uiState.recordingState.writtenRecordCount}",
            style = MaterialTheme.typography.bodyMedium,
        )
        uiState.recordingState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStartRecording,
                enabled = uiState.canStartRecording,
            ) {
                Text(text = "Start")
            }
            OutlinedButton(
                onClick = onStopRecording,
                enabled = uiState.canStopRecording,
            ) {
                Text(text = "Stop")
            }
        }
    }
}

@Composable
private fun DeviceConnectionList(
    devices: List<DeviceConnectionState>,
    onDisconnectDevice: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Devices",
            style = MaterialTheme.typography.titleMedium,
        )
        if (devices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "No connected devices yet. Connect one or more devices from the Scan screen.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            devices.forEach { device ->
                DeviceConnectionRow(
                    device = device,
                    onDisconnectDevice = onDisconnectDevice,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DeviceConnectionRow(
    device: DeviceConnectionState,
    onDisconnectDevice: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = device.deviceName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = device.macAddress,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = connectionStatusText(device),
                color = if (device.errorMessage == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = { onDisconnectDevice(device.macAddress) },
            enabled = device.lifecycleState != ConnectionLifecycleState.Disconnected,
        ) {
            Text(text = "Disconnect")
        }
    }
}

@Composable
private fun LiveRecordList(
    records: List<String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(records.size) {
        if (records.isNotEmpty()) {
            listState.animateScrollToItem(records.lastIndex)
        }
    }

    if (records.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = "Waiting for incoming packets.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
    ) {
        itemsIndexed(
            items = records,
            key = { index, _ -> index },
        ) { _, record ->
            Text(
                text = record,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
        }
    }
}

private fun connectionStatusText(device: DeviceConnectionState): String =
    device.errorMessage?.let { error ->
        "错误: $error"
    } ?: when (device.lifecycleState) {
        ConnectionLifecycleState.Connected -> "已连接"
        ConnectionLifecycleState.Connecting -> "连接中"
        ConnectionLifecycleState.Disconnected -> "未连接"
    }

@Preview(showBackground = true)
@Composable
private fun LiveScreenPreview() {
    BleDataCollectorTheme {
        LiveScreenContent(
            uiState = ConnectionUiState(
                devices = listOf(
                    DeviceConnectionState(
                        deviceName = "x_skiing",
                        macAddress = "3F:89:E5:1E:2A:EF",
                        lifecycleState = ConnectionLifecycleState.Connected,
                        errorMessage = null,
                    ),
                    DeviceConnectionState(
                        deviceName = "CM-1001",
                        macAddress = "00:11:22:33:44:55",
                        lifecycleState = ConnectionLifecycleState.Connecting,
                        errorMessage = null,
                    ),
                ),
                recordingState = RecordingUiState(
                    isRecording = true,
                    activeFileName = "ble-session_20260707_142530.txt",
                    writtenRecordCount = 2,
                ),
                formattedRecords = listOf(
                    "[15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF\nHEX: BE BB 42 AD BA FF",
                    "[15:44:23.012] 数据 -- CM-1001=00:11:22:33:44:55\nHEX: 9B 07 D8 FF FE FF",
                ),
            ),
            onDisconnectDevice = {},
            onDisconnectAll = {},
            onStartRecording = {},
            onStopRecording = {},
            onBack = {},
        )
    }
}
