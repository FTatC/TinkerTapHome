package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme

/** Detaljskärmen för en sparad enhet. Kopplar ViewModel till den tillståndslösa [DeviceDetailContent]. */
@Composable
fun DeviceDetailScreen(viewModel: DeviceDetailViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceDetailContent(uiState = uiState, onRetryClick = viewModel::onRetryClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailContent(
    uiState: DeviceDetailUiState,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = (uiState as? DeviceDetailUiState.Content)?.device?.name.orEmpty()
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (uiState) {
            DeviceDetailUiState.Loading -> LoadingState(contentModifier)
            DeviceDetailUiState.NotFound -> CenteredText(stringResource(R.string.device_not_found), contentModifier)
            is DeviceDetailUiState.Error -> CenteredText(storageErrorMessage(uiState.error), contentModifier)
            is DeviceDetailUiState.Content -> DeviceDetails(uiState, onRetryClick, contentModifier)
        }
    }
}

@Composable
private fun DeviceDetails(
    uiState: DeviceDetailUiState.Content,
    onRetryClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiState.device.address.value, style = MaterialTheme.typography.bodyMedium)
        Text(connectionStateLabel(uiState.connection), style = MaterialTheme.typography.titleMedium)
        uiState.error?.let { error ->
            Text(
                text = connectionErrorMessage(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (uiState.canRetry) {
            Button(onClick = onRetryClick) {
                Text(stringResource(R.string.device_retry))
            }
        }
    }
}

@Composable
private fun storageErrorMessage(error: DeviceError): String = when (error) {
    DeviceError.StorageUnavailable -> stringResource(R.string.devices_error_storage)
}

@Composable
private fun LoadingState(modifier: Modifier) {
    val description = stringResource(R.string.device_loading)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun CenteredText(text: String, modifier: Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
private fun DeviceDetailContentPreview() {
    TinkerTapTheme {
        DeviceDetailContent(
            uiState = DeviceDetailUiState.Content(
                device = Device(id = 1, address = checkNotNull(BleAddress.parse("AA:BB:CC:DD:EE:01")), name = "Lampa"),
                connection = ConnectionState.Connected,
            ),
            onRetryClick = {},
        )
    }
}
