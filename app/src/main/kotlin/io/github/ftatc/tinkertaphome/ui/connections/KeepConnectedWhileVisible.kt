package io.github.ftatc.tinkertaphome.ui.connections

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Håller sparade enheter anslutna medan appen syns. Läggs inuti `BlePermissionGate`, så att appen
 * bara ansluter när BLE-behörigheten finns.
 */
@Composable
fun KeepConnectedWhileVisible(viewModel: ForegroundConnectionsViewModel = hiltViewModel()) {
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }
}
