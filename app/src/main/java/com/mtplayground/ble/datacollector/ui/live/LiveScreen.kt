package com.mtplayground.ble.datacollector.ui.live

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

@Composable
fun LiveScreen(
    deviceName: String,
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
            text = "Data and recording placeholder",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Device: $deviceName",
            style = MaterialTheme.typography.bodyMedium,
        )
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
