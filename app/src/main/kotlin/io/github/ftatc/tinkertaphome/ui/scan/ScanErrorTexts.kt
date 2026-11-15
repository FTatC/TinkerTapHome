package io.github.ftatc.tinkertaphome.ui.scan

import androidx.annotation.StringRes
import io.github.ftatc.tinkertaphome.R

/**
 * En text som förklarar ett fel från Androids skanner, eller `null` för koder som inte har någon
 * egen text. Då visas en allmän text med koden.
 *
 * Koderna är `ScanCallback.SCAN_FAILED_*`. De skrivs ut här i stället för att importeras, så att
 * `ui` inte använder Androids Bluetooth-API:er (se CLAUDE.md).
 */
@StringRes
fun scanFailedMessage(code: Int): Int? = when (code) {
    SCANNING_TOO_FREQUENTLY -> R.string.scan_error_too_frequent
    FEATURE_UNSUPPORTED -> R.string.scan_error_unsupported
    APPLICATION_REGISTRATION_FAILED, INTERNAL_ERROR, OUT_OF_HARDWARE_RESOURCES -> R.string.scan_error_restart_bluetooth
    else -> null
}

private const val APPLICATION_REGISTRATION_FAILED = 2
private const val INTERNAL_ERROR = 3
private const val FEATURE_UNSUPPORTED = 4
private const val OUT_OF_HARDWARE_RESOURCES = 5

/** Android tillåter högst fem skanningar på 30 sekunder per app. */
private const val SCANNING_TOO_FREQUENTLY = 6
