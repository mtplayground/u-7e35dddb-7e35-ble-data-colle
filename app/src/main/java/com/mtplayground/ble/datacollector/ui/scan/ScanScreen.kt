package com.mtplayground.ble.datacollector.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtplayground.ble.datacollector.ui.theme.BleDataCollectorTheme

private const val PlaceholderDeviceName = "CM-placeholder"

@Composable
fun ScanScreen(
    onDeviceSelected: (String) -> Unit,
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
            text = "Scan",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Device list placeholder",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = { onDeviceSelected(PlaceholderDeviceName) }) {
            Text(text = PlaceholderDeviceName)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanScreenPreview() {
    BleDataCollectorTheme {
        ScanScreen(onDeviceSelected = {})
    }
}
