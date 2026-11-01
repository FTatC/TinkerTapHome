package io.github.ftatc.tinkertaphome.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R

/**
 * Visar [content] först när appen har BLE-behörigheterna. Annars visas en förklaring och en knapp
 * som ber om dem, eller en länk till appens inställningar om användaren har nekat permanent.
 *
 * Det här är den enda platsen där UI-lagret använder Android-API:er för behörigheter (se CLAUDE.md).
 */
@Composable
fun BlePermissionGate(
    viewModel: BlePermissionViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        val result = interpretPermissionResult(results) { permission ->
            activity?.shouldShowRequestPermissionRationale(permission) == true
        }
        viewModel.onPermissionResult(result)
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    BlePermissionContent(
        uiState = uiState,
        onRequestPermissions = { permissions -> launcher.launch(permissions.toTypedArray()) },
        onOpenSettings = {
            if (!context.tryStartActivity(appSettingsIntent(context))) viewModel.onSettingsUnavailable()
        },
        content = content,
    )
}

/** Tillståndslös del av [BlePermissionGate], så att den kan testas utan systemdialoger. */
@Composable
fun BlePermissionContent(
    uiState: BlePermissionUiState,
    onRequestPermissions: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (uiState) {
        BlePermissionUiState.Granted -> content()
        is BlePermissionUiState.Required -> PermissionExplanation(
            message = if (uiState.previouslyDenied) {
                R.string.permission_ble_rationale_denied
            } else {
                R.string.permission_ble_rationale
            },
            buttonLabel = R.string.permission_ble_grant,
            onClick = { onRequestPermissions(uiState.permissions) },
        )
        is BlePermissionUiState.PermanentlyDenied -> if (uiState.settingsUnavailable) {
            PermissionExplanation(message = R.string.permission_ble_settings_unavailable)
        } else {
            PermissionExplanation(
                message = R.string.permission_ble_permanently_denied,
                buttonLabel = R.string.permission_ble_open_settings,
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun PermissionExplanation(message: Int, buttonLabel: Int? = null, onClick: () -> Unit = {}) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.permission_ble_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(text = stringResource(message), textAlign = TextAlign.Center)
            if (buttonLabel != null) {
                Button(onClick = onClick) {
                    Text(stringResource(buttonLabel))
                }
            }
        }
    }
}

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
