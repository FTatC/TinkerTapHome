package io.github.ftatc.tinkertaphome.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.ui.device.connectionStateLabel
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme

/**
 * Skärmen med sparade enheter. Kopplar ViewModel till den tillståndslösa [DeviceListContent].
 *
 * @param onDeviceClick anropas med enhetens id när användaren väljer en enhet.
 */
@Composable
fun DeviceListScreen(
    onScanClick: () -> Unit,
    onDeviceClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceListContent(uiState = uiState, onScanClick = onScanClick, onDeviceClick = onDeviceClick, onSettingsClick = onSettingsClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListContent(
    uiState: DeviceListUiState,
    onScanClick: () -> Unit,
    onDeviceClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onScanClick) {
                Text(stringResource(R.string.devices_scan))
            }
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (uiState) {
            DeviceListUiState.Loading -> LoadingState(contentModifier)
            DeviceListUiState.Empty -> EmptyState(contentModifier)
            is DeviceListUiState.Content -> DeviceList(uiState.devices, onDeviceClick, contentModifier)
            is DeviceListUiState.Error -> ErrorState(uiState.error, contentModifier)
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    val description = stringResource(R.string.devices_loading)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.devices_empty))
    }
}

@Composable
private fun ErrorState(error: DeviceError, modifier: Modifier) {
    val message = when (error) {
        DeviceError.StorageUnavailable -> R.string.devices_error_storage
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(stringResource(message))
    }
}

@Composable
private fun DeviceList(devices: List<ConnectedDevice>, onDeviceClick: (Long) -> Unit, modifier: Modifier) {
    LazyColumn(modifier = modifier) {
        items(devices, key = { it.device.id }) { (device, connection) ->
            ListItem(
                modifier = Modifier.clickable { onDeviceClick(device.id) },
                headlineContent = { Text(device.name) },
                supportingContent = { Text(device.address.value) },
                trailingContent = { Text(connectionStateLabel(connection)) },
            )
        }
    }
}

@Preview
@Composable
private fun DeviceListContentPreview() {
    TinkerTapTheme {
        DeviceListContent(
            onScanClick = {},
            onDeviceClick = {},
            onSettingsClick = {},
            uiState = DeviceListUiState.Content(
                listOf(
                    ConnectedDevice(previewDevice(id = 1, address = "AA:BB:CC:DD:EE:01", name = "Lampa"), ConnectionState.Connected),
                    ConnectedDevice(previewDevice(id = 2, address = "AA:BB:CC:DD:EE:02", name = "Relä"), ConnectionState.Disconnected),
                ),
            ),
        )
    }
}

private fun previewDevice(id: Long, address: String, name: String) =
    Device(id = id, address = checkNotNull(BleAddress.parse(address)), name = name)
