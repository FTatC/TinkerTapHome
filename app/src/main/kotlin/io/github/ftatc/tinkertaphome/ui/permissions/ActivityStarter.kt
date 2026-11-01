package io.github.ftatc.tinkertaphome.ui.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

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
