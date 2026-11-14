package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import io.github.ftatc.tinkertaphome.domain.node.NodeOutputs
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import kotlin.time.Duration.Companion.seconds

/** Detaljskärmen för en sparad enhet. Kopplar ViewModel till den tillståndslösa [DeviceDetailContent]. */
@Composable
fun DeviceDetailScreen(viewModel: DeviceDetailViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceDetailContent(
        uiState = uiState,
        actions = DeviceDetailActions(
            onRetryClick = viewModel::onRetryClick,
            onOutputChange = viewModel::onOutputChange,
            onAllOffClick = viewModel::onAllOffClick,
            onTokenChange = viewModel::onTokenChange,
            onTokenSubmit = viewModel::onTokenSubmit,
        ),
    )
}

/** Händelserna från detaljskärmen. */
data class DeviceDetailActions(
    val onRetryClick: () -> Unit = {},
    val onOutputChange: (Output, Int) -> Unit = { _, _ -> },
    val onAllOffClick: () -> Unit = {},
    val onTokenChange: (String) -> Unit = {},
    val onTokenSubmit: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailContent(
    uiState: DeviceDetailUiState,
    actions: DeviceDetailActions,
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
            is DeviceDetailUiState.Content -> DeviceDetails(uiState, actions, contentModifier)
        }
    }
}

@Composable
private fun DeviceDetails(
    uiState: DeviceDetailUiState.Content,
    actions: DeviceDetailActions,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(uiState.device.address.value, style = MaterialTheme.typography.bodyMedium)
        uiState.firmwareVersion?.let { Text(stringResource(R.string.device_firmware, it), style = MaterialTheme.typography.bodyMedium) }
        Text(connectionStateLabel(uiState.connection), style = MaterialTheme.typography.titleMedium)
        if (uiState.isOpening) Text(stringResource(R.string.device_session_opening))
        StoppedBy(uiState)
        if (uiState.canRetry) {
            Button(onClick = actions.onRetryClick) {
                Text(stringResource(R.string.device_retry))
            }
        }
        if (uiState.asksForToken) TokenForm(uiState, actions)
        Outputs(uiState, actions)
    }
}

/** Felet som stoppade sessionen eller anslutningen. Sessionens fel visas först, eftersom det förklarar mest. */
@Composable
private fun StoppedBy(uiState: DeviceDetailUiState.Content) {
    val message = uiState.sessionError?.let { sessionErrorMessage(it) }
        ?: uiState.error?.let { connectionErrorMessage(it) }
        ?: return
    ErrorText(message)
}

@Composable
private fun TokenForm(uiState: DeviceDetailUiState.Content, actions: DeviceDetailActions) {
    OutlinedTextField(
        value = uiState.token,
        onValueChange = actions.onTokenChange,
        label = { Text(stringResource(R.string.save_node_token_label)) },
        singleLine = true,
        isError = uiState.tokenInvalid || uiState.tokenSaveFailed,
        supportingText = tokenSupportingText(uiState),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = actions.onTokenSubmit) {
        Text(stringResource(R.string.device_token_save))
    }
}

private fun tokenSupportingText(uiState: DeviceDetailUiState.Content): (@Composable () -> Unit)? = when {
    uiState.tokenInvalid -> { { Text(stringResource(R.string.save_node_error_token_invalid, AuthToken.MAX_LENGTH)) } }
    uiState.tokenSaveFailed -> { { Text(stringResource(R.string.device_token_save_failed)) } }
    else -> null
}

/** Utgångarna med sina kontroller, felet från senaste kommandot och knappen för att släcka allt. */
@Composable
private fun Outputs(uiState: DeviceDetailUiState.Content, actions: DeviceDetailActions) {
    if (uiState.outputs.isEmpty()) return
    HorizontalDivider()
    if (uiState.showsLastKnownValues) Text(stringResource(R.string.device_last_known), style = MaterialTheme.typography.bodyMedium)
    uiState.outputs.forEach { output ->
        OutputControl(output = output, enabled = uiState.canControl, onOutputChange = actions.onOutputChange)
    }
    uiState.commandError?.let { ErrorText(nodeFailureMessage(it)) }
    OutlinedButton(onClick = actions.onAllOffClick, enabled = uiState.canControl) {
        Text(stringResource(R.string.device_all_off))
    }
}

@Composable
private fun sessionErrorMessage(error: NodeSessionError): String = when (error) {
    NodeSessionError.TokenMissing -> stringResource(R.string.device_error_token_missing)
    is NodeSessionError.Node -> nodeFailureMessage(error.failure)
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
    val info = NodeInfo(
        firmwareVersion = "0.4.0",
        protocolVersion = 3,
        name = "TinkerTapNode-A1B2",
        pairingRequired = true,
        authRequired = true,
        authTimeout = 10.seconds,
        heartbeatTimeout = null,
        outputs = listOf(
            Output(OutputId(0), "led", OutputType.Digital),
            Output(OutputId(1), "fan", OutputType.Pwm),
            Output(OutputId(2), "servo", OutputType.Servo),
        ),
    )
    TinkerTapTheme {
        DeviceDetailContent(
            uiState = DeviceDetailUiState.Content(
                device = Device(id = 1, address = checkNotNull(BleAddress.parse("AA:BB:CC:DD:EE:01")), name = "Lampa"),
                connection = ConnectionState.Connected,
                session = NodeSession.Open(NodeOutputs(info, listOf(1, 128, 90))),
            ),
            actions = DeviceDetailActions(),
        )
    }
}
