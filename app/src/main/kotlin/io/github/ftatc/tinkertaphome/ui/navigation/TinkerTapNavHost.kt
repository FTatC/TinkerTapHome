package io.github.ftatc.tinkertaphome.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.ui.device.DeviceDetailScreen
import io.github.ftatc.tinkertaphome.ui.device.DeviceDetailViewModel
import io.github.ftatc.tinkertaphome.ui.devices.DeviceListScreen
import io.github.ftatc.tinkertaphome.ui.save.SaveDeviceScreen
import io.github.ftatc.tinkertaphome.ui.save.SaveDeviceViewModel
import io.github.ftatc.tinkertaphome.ui.scan.ScanScreen
import io.github.ftatc.tinkertaphome.ui.settings.SettingsScreen

/**
 * Appens navigation. Skärmarna skickas in som parametrar med de riktiga skärmarna som standard,
 * så att navigationen kan testas utan ViewModels och Hilt.
 */
@Composable
fun TinkerTapNavHost(
    navController: NavHostController = rememberNavController(),
    deviceListScreen: @Composable (onScanClick: () -> Unit, onDeviceClick: (Long) -> Unit, onSettingsClick: () -> Unit) -> Unit =
        { onScanClick, onDeviceClick, onSettingsClick ->
            DeviceListScreen(onScanClick = onScanClick, onDeviceClick = onDeviceClick, onSettingsClick = onSettingsClick)
        },
    deviceDetailScreen: @Composable (route: DeviceDetailRoute) -> Unit = { route -> DefaultDeviceDetailScreen(route) },
    scanScreen: @Composable (onDeviceClick: (ScannedDevice) -> Unit) -> Unit = { onDeviceClick ->
        ScanScreen(onDeviceClick = onDeviceClick)
    },
    saveDeviceScreen: @Composable (route: SaveDeviceRoute, onSaved: () -> Unit, onDismiss: () -> Unit) -> Unit =
        { route, onSaved, onDismiss -> DefaultSaveDeviceScreen(route, onSaved, onDismiss) },
    settingsScreen: @Composable () -> Unit = { SettingsScreen() },
) {
    NavHost(navController = navController, startDestination = DeviceListRoute) {
        composable<DeviceListRoute> {
            deviceListScreen(
                { navController.navigate(ScanRoute) },
                { deviceId -> navController.navigate(DeviceDetailRoute(deviceId)) },
                { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            settingsScreen()
        }
        composable<DeviceDetailRoute> { entry ->
            deviceDetailScreen(entry.toRoute<DeviceDetailRoute>())
        }
        composable<ScanRoute> {
            scanScreen { device -> navController.navigate(device.toSaveDeviceRoute()) }
        }
        dialog<SaveDeviceRoute> { entry ->
            saveDeviceScreen(
                entry.toRoute<SaveDeviceRoute>(),
                // När enheten är sparad går appen tillbaka till listan, där den nya enheten syns.
                { navController.popBackStack(DeviceListRoute, inclusive = false) },
                { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun DefaultSaveDeviceScreen(route: SaveDeviceRoute, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val viewModel = hiltViewModel<SaveDeviceViewModel, SaveDeviceViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    )
    SaveDeviceScreen(viewModel = viewModel, onSaved = onSaved, onDismiss = onDismiss)
}

@Composable
private fun DefaultDeviceDetailScreen(route: DeviceDetailRoute) {
    val viewModel = hiltViewModel<DeviceDetailViewModel, DeviceDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    )
    DeviceDetailScreen(viewModel = viewModel)
}

private fun ScannedDevice.toSaveDeviceRoute() = SaveDeviceRoute(address = address.value, suggestedName = name)
