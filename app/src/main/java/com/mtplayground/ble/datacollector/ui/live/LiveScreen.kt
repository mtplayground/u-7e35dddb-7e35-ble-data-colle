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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtplayground.ble.datacollector.ui.theme.BleDataCollectorTheme

@Composable
fun LiveScreen(
    deviceName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connectionViewModel: ConnectionViewModel = viewModel(),
) {
    val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(deviceName, connectionViewModel) {
        connectionViewModel.connect(deviceName)
        onDispose {
            connectionViewModel.disconnect()
        }
    }

    LiveScreenContent(
        deviceAddress = deviceName,
        uiState = connectionState,
        onDisconnect = connectionViewModel::disconnect,
        onRetry = connectionViewModel::retry,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun LiveScreenContent(
    deviceAddress: String,
    uiState: ConnectionUiState,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit,
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
            text = "Connection: ${uiState.lifecycleState.name}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Device: ${uiState.deviceAddress ?: deviceAddress}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Records: ${uiState.recordCount}",
            style = MaterialTheme.typography.bodyMedium,
        )
        uiState.errorMessage?.let { message ->
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
            OutlinedButton(
                onClick = onDisconnect,
                enabled = uiState.canDisconnect,
            ) {
                Text(text = "Disconnect")
            }
            Button(
                onClick = onRetry,
                enabled = uiState.canRetry,
            ) {
                Text(text = "Retry")
            }
        }
        LiveRecordList(
            records = uiState.formattedRecords,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onBack) {
            Text(text = "Back")
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

@Preview(showBackground = true)
@Composable
private fun LiveScreenPreview() {
    BleDataCollectorTheme {
        LiveScreenContent(
            deviceAddress = "3F:89:E5:1E:2A:EF",
            uiState = ConnectionUiState(
                deviceAddress = "3F:89:E5:1E:2A:EF",
                lifecycleState = com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState.Connected,
                formattedRecords = listOf(
                    "[15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF\nHEX: BE BB 42 AD BA FF",
                    "[15:44:23.012] 数据 -- x_skiing=3F:89:E5:1E:2A:EF\nHEX: 9B 07 D8 FF FE FF",
                ),
            ),
            onDisconnect = {},
            onRetry = {},
            onBack = {},
        )
    }
}
