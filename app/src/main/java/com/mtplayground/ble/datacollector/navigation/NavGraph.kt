package com.mtplayground.ble.datacollector.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mtplayground.ble.datacollector.ui.common.EnableBluetoothPrompt
import com.mtplayground.ble.datacollector.ui.common.PermissionGate
import com.mtplayground.ble.datacollector.ui.live.LiveScreen
import com.mtplayground.ble.datacollector.ui.scan.ScanScreen

private const val ScanRoute = "scan"
private const val LiveRoute = "live"

@Composable
fun BleNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ScanRoute,
        modifier = modifier,
    ) {
        composable(route = ScanRoute) {
            PermissionGate {
                EnableBluetoothPrompt {
                    ScanScreen(
                        onOpenLive = {
                            navController.navigate(LiveRoute)
                        },
                    )
                }
            }
        }

        composable(route = LiveRoute) {
            LiveScreen(
                deviceName = "",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
