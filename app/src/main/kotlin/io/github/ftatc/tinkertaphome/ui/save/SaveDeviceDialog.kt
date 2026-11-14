package io.github.ftatc.tinkertaphome.ui.save

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.usecase.SaveNodeError
import io.github.ftatc.tinkertaphome.ui.device.nodeFailureMessage

/**
 * Dialogen som parkopplar, namnger och sparar en hittad TinkerTapNode. Visas som en egen
 * dialogdestination i navigationen.
 *
 * @param onSaved anropas när noden är sparad, så att navigationen kan gå tillbaka till listan.
 * @param onDismiss anropas när användaren avbryter.
 */
@Composable
fun SaveDeviceScreen(
    viewModel: SaveDeviceViewModel,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    SaveDeviceDialog(
        uiState = uiState,
        actions = SaveDeviceActions(
            onPair = viewModel::onPairClick,
            onNameChange = viewModel::onNameChange,
            onTokenChange = viewModel::onTokenChange,
            onConfirm = viewModel::onSaveConfirm,
            onDismiss = onDismiss,
        ),
    )
}

/** Det användaren kan göra i dialogen. */
data class SaveDeviceActions(
    val onPair: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onTokenChange: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Tillståndslös del av [SaveDeviceScreen]. Navigationens dialogdestination ger redan ett eget
 * fönster, så innehållet ritas som en yta och inte som en `AlertDialog`, som skulle öppna ett till.
 */
@Composable
fun SaveDeviceDialog(uiState: SaveDeviceUiState, actions: SaveDeviceActions) {
    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.save_device_title), style = MaterialTheme.typography.headlineSmall)
            Text(uiState.address.value, style = MaterialTheme.typography.bodyMedium)
            when (uiState.step) {
                SaveStep.PressButton -> PressButtonStep(uiState, actions)
                SaveStep.Pairing -> PairingStep(actions)
                SaveStep.Details, SaveStep.Saving, SaveStep.Saved -> DetailsStep(uiState, actions)
            }
        }
    }
}

@Composable
private fun PressButtonStep(uiState: SaveDeviceUiState, actions: SaveDeviceActions) {
    Text(stringResource(R.string.save_node_press_button))
    Text(stringResource(R.string.save_node_already_paired), style = MaterialTheme.typography.bodySmall)
    uiState.pairError?.let { ErrorText(pairErrorMessage(it)) }
    Buttons(actions.onDismiss, confirmLabel = stringResource(R.string.save_node_pair), onConfirm = actions.onPair)
}

@Composable
private fun PairingStep(actions: SaveDeviceActions) {
    Text(stringResource(R.string.save_node_pairing))
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Buttons(actions.onDismiss)
}

@Composable
private fun DetailsStep(uiState: SaveDeviceUiState, actions: SaveDeviceActions) {
    val saving = uiState.step != SaveStep.Details
    val nameError = (uiState.saveError as? SaveNodeError.Save)?.error?.takeIf { it.isAboutTheName() }
    NameField(name = uiState.name, error = nameError, onNameChange = actions.onNameChange)
    if (uiState.showsTokenField) TokenField(uiState, actions.onTokenChange)
    uiState.saveError?.let { error -> generalSaveError(error)?.let { ErrorText(it) } }
    if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Buttons(
        onDismiss = actions.onDismiss,
        confirmLabel = stringResource(R.string.save_device_confirm),
        onConfirm = actions.onConfirm,
        confirmEnabled = !saving,
    )
}

@Composable
private fun NameField(name: String, error: SaveDeviceError?, onNameChange: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.save_device_name_label)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(saveErrorMessage(it)) } },
    )
}

/** Tokenet visas inte i klartext (fråga 3 i `docs/open-questions.md`). */
@Composable
private fun TokenField(uiState: SaveDeviceUiState, onTokenChange: (String) -> Unit) {
    val error = tokenErrorMessage(uiState)
    OutlinedTextField(
        value = uiState.token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.save_node_token_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error != null,
        supportingText = { Text(error ?: stringResource(R.string.save_node_token_hint)) },
    )
}

@Composable
private fun Buttons(
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_device_cancel)) }
        if (confirmLabel != null) {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** En misslyckad parkoppling beror oftast på att knappen inte trycktes eller att fönstret hann stängas. */
@Composable
private fun pairErrorMessage(failure: NodeFailure): String =
    if (failure == NodeFailure.Connection(ConnectionError.BondFailed)) {
        stringResource(R.string.save_node_error_bond_failed)
    } else {
        nodeFailureMessage(failure)
    }

@Composable
private fun tokenErrorMessage(uiState: SaveDeviceUiState): String? = when {
    uiState.tokenInvalid || uiState.saveError == SaveNodeError.TokenMissing ->
        stringResource(R.string.save_node_error_token_invalid, AuthToken.MAX_LENGTH)
    uiState.saveError == SaveNodeError.Node(NodeFailure.Command(CommandError.AuthFailed)) ->
        stringResource(R.string.node_error_auth_failed)
    else -> null
}

/** Fel som inte hör till namnet eller tokenet och därför visas under fälten, eller `null`. */
@Composable
private fun generalSaveError(error: SaveNodeError): String? = when (error) {
    is SaveNodeError.Save -> error.error.takeUnless { it.isAboutTheName() }?.let { saveErrorMessage(it) }
    SaveNodeError.TokenMissing -> null
    is SaveNodeError.Node -> error.failure
        .takeUnless { it == NodeFailure.Command(CommandError.AuthFailed) }
        ?.let { nodeFailureMessage(it) }
}

private fun SaveDeviceError.isAboutTheName(): Boolean =
    this == SaveDeviceError.NameMissing || this == SaveDeviceError.NameTooLong

@Composable
private fun saveErrorMessage(error: SaveDeviceError): String = when (error) {
    SaveDeviceError.NameMissing -> stringResource(R.string.save_device_error_name_missing)
    SaveDeviceError.NameTooLong -> pluralStringResource(
        R.plurals.save_device_error_name_too_long,
        DeviceNames.MAX_LENGTH,
        DeviceNames.MAX_LENGTH,
    )
    SaveDeviceError.AlreadySaved -> stringResource(R.string.save_device_error_already_saved)
    SaveDeviceError.StorageUnavailable -> stringResource(R.string.save_device_error_storage)
}
