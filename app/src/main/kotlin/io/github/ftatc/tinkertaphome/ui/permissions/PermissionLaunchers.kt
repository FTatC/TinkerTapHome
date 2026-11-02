package io.github.ftatc.tinkertaphome.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable

/**
 * Returnerar en funktion som visar systemets behörighetsdialog för de givna behörigheterna.
 * Svaret tolkas med [interpretPermissionResult] och skickas till [onResult].
 */
@Composable
internal fun rememberPermissionRequest(onResult: (PermissionRequestResult) -> Unit): (List<String>) -> Unit {
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        val result = interpretPermissionResult(results) { permission ->
            activity?.shouldShowRequestPermissionRationale(permission) == true
        }
        onResult(result)
    }
    return { permissions -> launcher.launch(permissions.toTypedArray()) }
}

/** Returnerar en funktion som öppnar appens inställningar, eller anropar [onUnavailable] om de saknas. */
@Composable
internal fun appSettingsOpener(onUnavailable: () -> Unit): () -> Unit =
    activityStarter(intent = ::appSettingsIntent, onUnavailable = onUnavailable)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
