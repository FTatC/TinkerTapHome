package io.github.ftatc.tinkertaphome.ui.scan

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice

/**
 * UI-tillståndet för skanningsskärmen.
 *
 * Hittade enheter ligger kvar när skanningen har stoppats, så att användaren kan välja bland dem.
 *
 * @property scanError Felet som stoppade den senaste skanningen, eller `null`.
 */
data class ScanUiState(
    val devices: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: BleError? = null,
)

/** Tillståndet efter ett resultat från skannern: en ny lista eller ett fel. */
internal fun ScanUiState.withScanResult(outcome: Outcome<List<ScannedDevice>, BleError>): ScanUiState =
    when (outcome) {
        is Outcome.Success -> copy(devices = outcome.value)
        is Outcome.Failure -> copy(scanError = outcome.error)
    }
