package io.github.ftatc.tinkertaphome.ui.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Startar [intent] och returnerar `false` i stället för att krascha om ingen aktivitet kan ta emot den.
 * På hanterade enheter, till exempel med arbetsprofil eller kioskläge, kan systemets skärmar saknas.
 */
internal fun Context.tryStartActivity(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

/**
 * Startar launchern med [input] och returnerar `false` i stället för att krascha om ingen aktivitet
 * kan ta emot den, på samma sätt som [tryStartActivity].
 */
internal fun <I> ActivityResultLauncher<I>.tryLaunch(input: I): Boolean =
    try {
        launch(input)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

/**
 * Returnerar en funktion som startar intentet från [intent]. Finns ingen aktivitet som kan ta emot
 * det anropas [onUnavailable] i stället.
 */
@Composable
internal fun activityStarter(intent: (Context) -> Intent, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    return { if (!context.tryStartActivity(intent(context))) onUnavailable() }
}
