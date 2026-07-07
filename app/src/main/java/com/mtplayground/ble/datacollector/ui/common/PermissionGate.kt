package com.mtplayground.ble.datacollector.ui.common

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mtplayground.ble.datacollector.permissions.PermissionController
import com.mtplayground.ble.datacollector.permissions.PermissionUiState
import com.mtplayground.ble.datacollector.permissions.RuntimePermission
import com.mtplayground.ble.datacollector.ui.theme.BleDataCollectorTheme

@Composable
fun PermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var requestAttempted by rememberSaveable { mutableStateOf(false) }
    var permissionActionError by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionState by remember {
        mutableStateOf(PermissionController.evaluate(context, requestAttempted))
    }

    fun refreshPermissionState() {
        permissionState = PermissionController.evaluate(context, requestAttempted)
        if (permissionState.allGranted) {
            permissionActionError = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestAttempted = true
        permissionState = PermissionController.evaluate(context, requestAttempted = true)
    }

    DisposableEffect(lifecycleOwner, context, requestAttempted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (permissionState.allGranted) {
        content()
    } else {
        PermissionRequiredContent(
            state = permissionState,
            actionError = permissionActionError,
            modifier = modifier,
            onRequestPermissions = {
                requestAttempted = true
                permissionActionError = null
                val missingPermissions = permissionState.missingPermissions
                    .map(RuntimePermission::value)
                    .toTypedArray()
                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions)
                } else {
                    refreshPermissionState()
                }
            },
            onOpenSettings = {
                permissionActionError = null
                try {
                    context.startActivity(PermissionController.appSettingsIntent(context))
                } catch (_: ActivityNotFoundException) {
                    permissionActionError = "Android app settings are not available on this device."
                } catch (_: SecurityException) {
                    permissionActionError = "Android blocked opening app settings."
                }
            },
        )
    }
}

@Composable
private fun PermissionRequiredContent(
    state: PermissionUiState,
    actionError: String?,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
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
            text = "Permissions required",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = permissionMessage(state),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = state.missingPermissions.joinToString(separator = "\n") { permission ->
                "- ${permission.label}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        actionError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isPermanentlyDenied) {
            OutlinedButton(onClick = onOpenSettings) {
                Text(text = "Open Settings")
            }
        } else {
            Button(onClick = onRequestPermissions) {
                Text(text = "Grant Permissions")
            }
        }
    }
}

private fun permissionMessage(state: PermissionUiState): String = when {
    state.isPermanentlyDenied ->
        "Enable the missing permissions in Android app settings before scanning."

    state.shouldShowRationale ->
        "Bluetooth scanning needs these permissions before devices can be listed."

    else ->
        "Grant the required permissions to enable scanning."
}

@Preview(showBackground = true)
@Composable
private fun PermissionRequiredContentPreview() {
    BleDataCollectorTheme {
        PermissionRequiredContent(
            state = PermissionUiState(
                requiredPermissions = listOf(
                    RuntimePermission("android.permission.BLUETOOTH_SCAN", "Nearby device scanning"),
                ),
                missingPermissions = listOf(
                    RuntimePermission("android.permission.BLUETOOTH_SCAN", "Nearby device scanning"),
                ),
                permanentlyDeniedPermissions = emptyList(),
                shouldShowRationale = true,
            ),
            actionError = null,
            onRequestPermissions = {},
            onOpenSettings = {},
        )
    }
}
