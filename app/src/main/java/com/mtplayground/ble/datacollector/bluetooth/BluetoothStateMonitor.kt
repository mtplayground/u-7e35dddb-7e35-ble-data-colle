package com.mtplayground.ble.datacollector.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class BluetoothReadiness {
    Unavailable,
    Disabled,
    TurningOn,
    Enabled,
    TurningOff,
}

data class BluetoothAdapterStatus(
    val readiness: BluetoothReadiness,
) {
    val isReady: Boolean = readiness == BluetoothReadiness.Enabled
    val canRequestEnable: Boolean = readiness == BluetoothReadiness.Disabled
}

object BluetoothStateMonitor {
    @Composable
    fun rememberBluetoothAdapterStatus(): State<BluetoothAdapterStatus> {
        val context = LocalContext.current.applicationContext
        val lifecycleOwner = LocalLifecycleOwner.current
        val status = remember {
            mutableStateOf(currentStatus(context))
        }

        DisposableEffect(context, lifecycleOwner) {
            fun refresh() {
                status.value = currentStatus(context)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        refresh()
                    }
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refresh()
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            lifecycleOwner.lifecycle.addObserver(observer)
            refresh()

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                context.unregisterReceiver(receiver)
            }
        }

        return status
    }

    fun enableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    fun currentStatus(context: Context): BluetoothAdapterStatus {
        val adapter = bluetoothAdapter(context)
            ?: return BluetoothAdapterStatus(BluetoothReadiness.Unavailable)

        return BluetoothAdapterStatus(readiness = adapter.readiness())
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.readiness(): BluetoothReadiness = try {
        when (state) {
            BluetoothAdapter.STATE_ON -> BluetoothReadiness.Enabled
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothReadiness.TurningOn
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothReadiness.TurningOff
            else -> BluetoothReadiness.Disabled
        }
    } catch (_: SecurityException) {
        BluetoothReadiness.Disabled
    }
}
