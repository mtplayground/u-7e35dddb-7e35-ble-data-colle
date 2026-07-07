package com.mtplayground.ble.datacollector.ui.common

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtplayground.ble.datacollector.bluetooth.BluetoothAdapterStatus
import com.mtplayground.ble.datacollector.bluetooth.BluetoothReadiness
import com.mtplayground.ble.datacollector.bluetooth.BluetoothStateMonitor
import com.mtplayground.ble.datacollector.ui.theme.BleDataCollectorTheme

@Composable
fun EnableBluetoothPrompt(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val adapterStatus by BluetoothStateMonitor.rememberBluetoothAdapterStatus()
    var promptError by rememberSaveable { mutableStateOf<String?>(null) }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        promptError = null
    }

    if (adapterStatus.isReady) {
        content()
    } else {
        EnableBluetoothPromptContent(
            status = adapterStatus,
            promptError = promptError,
            modifier = modifier,
            onRequestEnable = {
                promptError = null
                try {
                    enableBluetoothLauncher.launch(BluetoothStateMonitor.enableBluetoothIntent())
                } catch (_: ActivityNotFoundException) {
                    promptError = "Bluetooth settings are not available on this device."
                } catch (_: SecurityException) {
                    promptError = "Bluetooth permission is required before Bluetooth can be enabled."
                }
            },
        )
    }
}

@Composable
private fun EnableBluetoothPromptContent(
    status: BluetoothAdapterStatus,
    promptError: String?,
    onRequestEnable: () -> Unit,
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
            text = bluetoothTitle(status),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = bluetoothMessage(status),
            style = MaterialTheme.typography.bodyLarge,
        )

        promptError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when {
            status.canRequestEnable -> {
                Button(onClick = onRequestEnable) {
                    Text(text = "Enable Bluetooth")
                }
            }

            status.readiness == BluetoothReadiness.TurningOn ||
                status.readiness == BluetoothReadiness.TurningOff -> {
                CircularProgressIndicator()
            }
        }
    }
}

private fun bluetoothTitle(status: BluetoothAdapterStatus): String = when (status.readiness) {
    BluetoothReadiness.Unavailable -> "Bluetooth unavailable"
    BluetoothReadiness.Enabled -> "Bluetooth enabled"
    BluetoothReadiness.TurningOn -> "Bluetooth turning on"
    BluetoothReadiness.TurningOff -> "Bluetooth turning off"
    BluetoothReadiness.Disabled -> "Bluetooth disabled"
}

private fun bluetoothMessage(status: BluetoothAdapterStatus): String = when (status.readiness) {
    BluetoothReadiness.Unavailable ->
        "This device does not report a Bluetooth adapter."

    BluetoothReadiness.Enabled ->
        "Bluetooth is ready."

    BluetoothReadiness.TurningOn ->
        "Waiting for Bluetooth to finish turning on."

    BluetoothReadiness.TurningOff ->
        "Waiting for Bluetooth state to settle."

    BluetoothReadiness.Disabled ->
        "Bluetooth must be enabled before scanning."
}

@Preview(showBackground = true)
@Composable
private fun EnableBluetoothPromptContentPreview() {
    BleDataCollectorTheme {
        EnableBluetoothPromptContent(
            status = BluetoothAdapterStatus(BluetoothReadiness.Disabled),
            promptError = null,
            onRequestEnable = {},
        )
    }
}
