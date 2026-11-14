package io.github.ftatc.tinkertaphome.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme

/**
 * Skärmen som söker efter nya enheter. Skanningen startar när skärmen syns och stoppas när den
 * inte längre syns, så att den inte drar batteri i bakgrunden. Dialogen för att spara en enhet
 * visas ovanpå skärmen, så skanningen fortsätter medan den är öppen.
 *
 * @param onDeviceClick anropas när användaren väljer en hittad enhet.
 */
@Composable
fun ScanScreen(
    onDeviceClick: (ScannedDevice) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.startScan()
        onStopOrDispose { viewModel.stopScan() }
    }

    ScanContent(uiState = uiState, onScanAgain = viewModel::startScan, onDeviceClick = onDeviceClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContent(
    uiState: ScanUiState,
    onScanAgain: () -> Unit,
    onDeviceClick: (ScannedDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScanStatus(uiState, onScanAgain)
            when {
                uiState.devices.isNotEmpty() -> FoundDevices(uiState.devices, onDeviceClick)
                uiState.isScanning -> CenteredText(stringResource(R.string.scan_searching))
                uiState.scanError == null -> CenteredText(stringResource(R.string.scan_none_found))
            }
        }
    }
}

@Composable
private fun ScanStatus(uiState: ScanUiState, onScanAgain: () -> Unit) {
    when {
        uiState.isScanning -> {
            val description = stringResource(R.string.scan_in_progress)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            )
        }
        uiState.scanError != null -> StatusRow(errorMessage(uiState.scanError), R.string.scan_retry, onScanAgain)
        else -> StatusRow(stringResource(R.string.scan_finished), R.string.scan_again, onScanAgain)
    }
}

@Composable
private fun StatusRow(message: String, buttonLabel: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(stringResource(buttonLabel)) }
    }
}

@Composable
private fun errorMessage(error: BleError): String = when (error) {
    BleError.BluetoothOff -> stringResource(R.string.scan_error_bluetooth_off)
    BleError.PermissionMissing -> stringResource(R.string.scan_error_permission)
    is BleError.ScanFailed -> stringResource(R.string.scan_error_failed, error.code)
}

@Composable
private fun FoundDevices(devices: List<ScannedDevice>, onDeviceClick: (ScannedDevice) -> Unit) {
    LazyColumn {
        items(devices, key = { it.address.value }) { device ->
            // Bara TinkerTapNode kan sparas (beslut i docs/roadmap.md, fas 6). Andra enheter visas men går inte att välja.
            ListItem(
                modifier = Modifier.clickable(enabled = device.isTinkerTapNode) { onDeviceClick(device) },
                headlineContent = { Text(device.name ?: stringResource(R.string.scan_unknown_device)) },
                supportingContent = { DeviceDescription(device) },
                trailingContent = { Text(stringResource(R.string.scan_rssi, device.rssi)) },
            )
        }
    }
}

@Composable
private fun DeviceDescription(device: ScannedDevice) {
    val address = device.address.value
    Text(if (device.isTinkerTapNode) address else stringResource(R.string.scan_not_supported, address))
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
private fun ScanContentPreview() {
    TinkerTapTheme {
        ScanContent(
            uiState = ScanUiState(
                devices = listOf(
                    ScannedDevice(checkNotNull(BleAddress.parse("AA:BB:CC:DD:EE:01")), "TinkerTapNode-EE01", -48, isTinkerTapNode = true),
                    ScannedDevice(checkNotNull(BleAddress.parse("AA:BB:CC:DD:EE:02")), null, -71),
                ),
                isScanning = true,
            ),
            onScanAgain = {},
            onDeviceClick = {},
        )
    }
}
