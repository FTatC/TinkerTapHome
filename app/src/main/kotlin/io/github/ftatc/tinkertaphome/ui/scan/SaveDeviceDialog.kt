package io.github.ftatc.tinkertaphome.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError

/** Dialog där användaren väljer namn på en hittad enhet och sparar den. */
@Composable
internal fun SaveDeviceDialog(
    dialog: SaveDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_device_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(dialog.device.address.value, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.save_device_name_label)) },
                    singleLine = true,
                    isError = dialog.error != null,
                    supportingText = dialog.error?.let { error -> { Text(saveErrorMessage(error)) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSaving) {
                Text(stringResource(R.string.save_device_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_device_cancel)) }
        },
    )
}

@Composable
private fun saveErrorMessage(error: SaveDeviceError): String = when (error) {
    SaveDeviceError.NameMissing -> stringResource(R.string.save_device_error_name_missing)
    SaveDeviceError.AlreadySaved -> stringResource(R.string.save_device_error_already_saved)
    SaveDeviceError.StorageUnavailable -> stringResource(R.string.save_device_error_storage)
}
