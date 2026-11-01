package io.github.ftatc.tinkertaphome.ui.scan

import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice

/**
 * UI-tillståndet för skanningsskärmen.
 *
 * Hittade enheter ligger kvar när skanningen har stoppats, så att användaren kan välja bland dem.
 *
 * @property error Felet som stoppade den senaste skanningen, eller `null`.
 */
data class ScanUiState(
    val devices: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val error: BleError? = null,
)
