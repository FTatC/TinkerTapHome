package io.github.ftatc.tinkertaphome.ui.save

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError

/**
 * Dialogen som namnger och sparar en hittad enhet. Visas som en egen dialogdestination i navigationen.
 *
 * @param onSaved anropas när enheten är sparad, så att navigationen kan gå tillbaka till listan.
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
        onNameChange = viewModel::onNameChange,
        onConfirm = viewModel::onSaveConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Tillståndslös del av [SaveDeviceScreen]. Navigationens dialogdestination ger redan ett eget
 * fönster, så innehållet ritas som en yta och inte som en `AlertDialog`, som skulle öppna ett till.
 */
@Composable
fun SaveDeviceDialog(
    uiState: SaveDeviceUiState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.save_device_title), style = MaterialTheme.typography.headlineSmall)
            Text(uiState.address.value, style = MaterialTheme.typography.bodyMedium)
            NameField(name = uiState.name, error = uiState.saveError, onNameChange = onNameChange)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_device_cancel)) }
                TextButton(onClick = onConfirm, enabled = !uiState.isSaving) {
                    Text(stringResource(R.string.save_device_confirm))
                }
            }
        }
    }
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
