package com.mtplayground.ble.datacollector.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class RuntimePermission(
    val value: String,
    val label: String,
)

data class PermissionUiState(
    val requiredPermissions: List<RuntimePermission>,
    val missingPermissions: List<RuntimePermission>,
    val permanentlyDeniedPermissions: List<RuntimePermission>,
    val shouldShowRationale: Boolean,
) {
    val allGranted: Boolean = missingPermissions.isEmpty()
    val isPermanentlyDenied: Boolean = permanentlyDeniedPermissions.isNotEmpty()
}

object PermissionController {
    fun requiredPermissions(): List<RuntimePermission> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            RuntimePermission(
                value = Manifest.permission.BLUETOOTH_SCAN,
                label = "Nearby device scanning",
            ),
            RuntimePermission(
                value = Manifest.permission.BLUETOOTH_CONNECT,
                label = "Nearby device connection",
            ),
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(
            RuntimePermission(
                value = Manifest.permission.ACCESS_FINE_LOCATION,
                label = "Location for Bluetooth scanning",
            ),
        )

        else -> emptyList()
    }

    fun evaluate(context: Context, requestAttempted: Boolean): PermissionUiState {
        val requiredPermissions = requiredPermissions()
        val missingPermissions = requiredPermissions.filterNot { permission ->
            isGranted(context = context, permission = permission.value)
        }
        val rationalePermissions = missingPermissions.filter { permission ->
            shouldShowRationale(context = context, permission = permission.value)
        }
        val permanentlyDeniedPermissions = if (requestAttempted) {
            missingPermissions.filterNot { permission ->
                shouldShowRationale(context = context, permission = permission.value)
            }
        } else {
            emptyList()
        }

        return PermissionUiState(
            requiredPermissions = requiredPermissions,
            missingPermissions = missingPermissions,
            permanentlyDeniedPermissions = permanentlyDeniedPermissions,
            shouldShowRationale = rationalePermissions.isNotEmpty(),
        )
    }

    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun shouldShowRationale(context: Context, permission: String): Boolean {
        val activity = context.findActivity() ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
