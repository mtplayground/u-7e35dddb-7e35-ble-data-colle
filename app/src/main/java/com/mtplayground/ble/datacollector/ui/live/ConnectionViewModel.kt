package com.mtplayground.ble.datacollector.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtplayground.ble.datacollector.ble.BleManager
import com.mtplayground.ble.datacollector.ble.ConnectResult
import com.mtplayground.ble.datacollector.ble.ConnectionLifecycleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ConnectionUiState(
    val deviceAddress: String? = null,
    val lifecycleState: ConnectionLifecycleState = ConnectionLifecycleState.Disconnected,
    val errorMessage: String? = null,
) {
    val isConnected: Boolean = lifecycleState == ConnectionLifecycleState.Connected
    val isConnecting: Boolean = lifecycleState == ConnectionLifecycleState.Connecting
    val canDisconnect: Boolean = lifecycleState != ConnectionLifecycleState.Disconnected
    val canRetry: Boolean = deviceAddress != null &&
        lifecycleState == ConnectionLifecycleState.Disconnected
}

class ConnectionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bleManager = BleManager(application)
    private val targetDeviceAddress = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConnectionUiState> = combine(
        targetDeviceAddress,
        bleManager.connectionState,
        bleManager.connectionError,
    ) { deviceAddress, lifecycleState, errorMessage ->
        ConnectionUiState(
            deviceAddress = deviceAddress,
            lifecycleState = lifecycleState,
            errorMessage = errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ConnectionUiState(),
    )

    fun connect(deviceAddress: String) {
        targetDeviceAddress.value = deviceAddress
        bleManager.connect(deviceAddress)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun retry() {
        when (bleManager.reconnect()) {
            ConnectResult.Started,
            ConnectResult.AlreadyConnecting,
            -> Unit

            ConnectResult.BluetoothUnavailable,
            ConnectResult.BluetoothDisabled,
            ConnectResult.InvalidAddress,
            ConnectResult.PermissionDenied,
            ConnectResult.Failed,
            -> Unit
        }
    }

    fun clearError() {
        bleManager.clearConnectionError()
    }

    override fun onCleared() {
        bleManager.disconnect()
        super.onCleared()
    }
}
