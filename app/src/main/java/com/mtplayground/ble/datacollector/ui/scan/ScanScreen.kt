package com.mtplayground.ble.datacollector.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtplayground.ble.datacollector.ble.model.DiscoveredDevice
import com.mtplayground.ble.datacollector.ui.theme.BleDataCollectorTheme

@Composable
fun ScanScreen(
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        viewModel.startScan()
        onDispose {
            viewModel.stopScan()
        }
    }

    ScanScreenContent(
        uiState = uiState,
        modifier = modifier,
        onStartScan = viewModel::startScan,
        onStopScan = viewModel::stopScan,
        onClearDevices = viewModel::clearDevices,
        onConnectDevice = viewModel::connectDevice,
        onDisconnectDevice = viewModel::disconnectDevice,
        onDeviceSelected = { device ->
            viewModel.selectDevice(device)
            onOpenLive()
        },
    )
}

@Composable
private fun ScanScreenContent(
    uiState: ScanUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onClearDevices: () -> Unit,
    onConnectDevice: (DiscoveredDevice) -> Unit,
    onDisconnectDevice: (DiscoveredDevice) -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScanHeader(isScanning = uiState.isScanning)
        ScanControls(
            isScanning = uiState.isScanning,
            hasDevices = uiState.devices.isNotEmpty(),
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onClearDevices = onClearDevices,
        )

        uiState.statusMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (uiState.devices.isEmpty()) {
            EmptyScanState(isScanning = uiState.isScanning)
        } else {
            DeviceList(
                devices = uiState.devices,
                onConnectDevice = onConnectDevice,
                onDisconnectDevice = onDisconnectDevice,
                onDeviceSelected = onDeviceSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScanHeader(isScanning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Scan",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (isScanning) "Scanning for filtered devices" else "Scan paused",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ScanControls(
    isScanning: Boolean,
    hasDevices: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onClearDevices: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isScanning) {
            OutlinedButton(onClick = onStopScan) {
                Text(text = "Stop")
            }
        } else {
            Button(onClick = onStartScan) {
                Text(text = "Scan")
            }
        }

        OutlinedButton(
            onClick = onClearDevices,
            enabled = hasDevices,
        ) {
            Text(text = "Clear")
        }
    }
}

@Composable
private fun EmptyScanState(isScanning: Boolean) {
    Text(
        text = if (isScanning) {
            "Waiting for devices named CM-* or x_skiing*."
        } else {
            "No devices found."
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun DeviceList(
    devices: List<ScanDeviceUiState>,
    onConnectDevice: (DiscoveredDevice) -> Unit,
    onDisconnectDevice: (DiscoveredDevice) -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(
            items = devices,
            key = { deviceState -> deviceState.device.macAddress },
        ) { deviceState ->
            DeviceRow(
                deviceState = deviceState,
                onConnect = { onConnectDevice(deviceState.device) },
                onDisconnect = { onDisconnectDevice(deviceState.device) },
                onClick = { onDeviceSelected(deviceState.device) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DeviceRow(
    deviceState: ScanDeviceUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClick: () -> Unit,
) {
    val device = deviceState.device
    ListItem(
        headlineContent = {
            Text(text = device.name)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = device.macAddress)
                Text(
                    text = connectionStatusText(deviceState),
                    color = if (deviceState.connectionError == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = signalStrengthLabel(device.rssi),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                DeviceConnectionAction(
                    deviceState = deviceState,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DeviceConnectionAction(
    deviceState: ScanDeviceUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when {
        deviceState.canDisconnect -> {
            OutlinedButton(onClick = onDisconnect) {
                Text(text = "Disconnect")
            }
        }

        else -> {
            Button(
                onClick = onConnect,
                enabled = deviceState.canConnect,
            ) {
                Text(text = "Connect")
            }
        }
    }
}

private fun connectionStatusText(deviceState: ScanDeviceUiState): String =
    deviceState.connectionError?.let { error ->
        "${deviceState.stateLabel}: $error"
    } ?: deviceState.stateLabel

private fun signalStrengthLabel(rssi: Int): String = when {
    rssi >= -60 -> "Strong"
    rssi >= -80 -> "Fair"
    else -> "Weak"
}

@Preview(showBackground = true)
@Composable
private fun ScanScreenContentPreview() {
    BleDataCollectorTheme {
        ScanScreenContent(
            uiState = ScanUiState(
                devices = listOf(
                    ScanDeviceUiState(
                        device = DiscoveredDevice(
                            name = "CM-1001",
                            macAddress = "00:11:22:33:44:55",
                            rssi = -58,
                            lastSeenMillis = 0L,
                        ),
                        connectionState = com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState.Connected,
                    ),
                    ScanDeviceUiState(
                        device = DiscoveredDevice(
                            name = "x_skiing-alpha",
                            macAddress = "AA:BB:CC:DD:EE:FF",
                            rssi = -82,
                            lastSeenMillis = 0L,
                        ),
                    ),
                ),
                isScanning = true,
            ),
            onStartScan = {},
            onStopScan = {},
            onClearDevices = {},
            onConnectDevice = {},
            onDisconnectDevice = {},
            onDeviceSelected = {},
        )
    }
}
