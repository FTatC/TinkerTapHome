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
import io.github.ftatc.tinkertaphome.ui.devices.DeviceListScreen
import io.github.ftatc.tinkertaphome.ui.save.SaveDeviceScreen
import io.github.ftatc.tinkertaphome.ui.save.SaveDeviceViewModel
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
    scanScreen: @Composable (onDeviceClick: (ScannedDevice) -> Unit) -> Unit = { onDeviceClick ->
        ScanScreen(onDeviceClick = onDeviceClick)
    },
    saveDeviceScreen: @Composable (route: SaveDeviceRoute, onSaved: () -> Unit, onDismiss: () -> Unit) -> Unit =
        { route, onSaved, onDismiss -> DefaultSaveDeviceScreen(route, onSaved, onDismiss) },
) {
    NavHost(navController = navController, startDestination = DeviceListRoute) {
        composable<DeviceListRoute> {
            deviceListScreen { navController.navigate(ScanRoute) }
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

private fun ScannedDevice.toSaveDeviceRoute() = SaveDeviceRoute(address = address.value, suggestedName = name)
