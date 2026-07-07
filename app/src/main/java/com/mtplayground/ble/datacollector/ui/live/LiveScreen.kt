package com.mtplayground.ble.datacollector.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
            text = "Data and recording placeholder",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Device: $deviceName",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Connection: ${connectionState.lifecycleState.name}",
            style = MaterialTheme.typography.bodyMedium,
        )
        connectionState.errorMessage?.let { message ->
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
                onClick = connectionViewModel::disconnect,
                enabled = connectionState.canDisconnect,
            ) {
                Text(text = "Disconnect")
            }
            Button(
                onClick = connectionViewModel::retry,
                enabled = connectionState.canRetry,
            ) {
                Text(text = "Retry")
            }
        }
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveScreenPreview() {
    BleDataCollectorTheme {
        LiveScreen(
            deviceName = "CM-placeholder",
            onBack = {},
        )
    }
}
