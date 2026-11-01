package io.github.ftatc.tinkertaphome.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.ftatc.tinkertaphome.ui.devices.DeviceListScreen
import io.github.ftatc.tinkertaphome.ui.scan.ScanScreen

/**
 * Appens navigation. Skärmarna skickas in som parametrar med de riktiga skärmarna som standard,
 * så att navigationen kan testas utan ViewModels och Hilt.
 */
@Composable
fun TinkerTapNavHost(
    navController: NavHostController = rememberNavController(),
    deviceListScreen: @Composable (onScanClick: () -> Unit) -> Unit = { onScanClick ->
        DeviceListScreen(onScanClick = onScanClick)
    },
    scanScreen: @Composable () -> Unit = { ScanScreen() },
) {
    NavHost(navController = navController, startDestination = DeviceListRoute) {
        composable<DeviceListRoute> {
            deviceListScreen { navController.navigate(ScanRoute) }
        }
        composable<ScanRoute> {
            scanScreen()
        }
    }
}
