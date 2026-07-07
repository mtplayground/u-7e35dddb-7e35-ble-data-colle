package com.mtplayground.ble.datacollector.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtplayground.ble.datacollector.ui.live.LiveScreen
import com.mtplayground.ble.datacollector.ui.scan.ScanScreen

private const val ScanRoute = "scan"
private const val LiveRoute = "live"
private const val DeviceNameArg = "deviceName"
private const val LiveRoutePattern = "$LiveRoute/{$DeviceNameArg}"

@Composable
fun BleNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ScanRoute,
        modifier = modifier,
    ) {
        composable(route = ScanRoute) {
            ScanScreen(
                onDeviceSelected = { deviceName ->
                    navController.navigate("$LiveRoute/${Uri.encode(deviceName)}")
                },
            )
        }

        composable(
            route = LiveRoutePattern,
            arguments = listOf(
                navArgument(DeviceNameArg) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            LiveScreen(
                deviceName = backStackEntry.arguments?.getString(DeviceNameArg).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
