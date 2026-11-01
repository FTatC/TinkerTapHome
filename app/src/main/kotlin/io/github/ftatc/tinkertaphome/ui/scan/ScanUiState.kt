package io.github.ftatc.tinkertaphome.ui.scan

import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice

/**
 * UI-tillståndet för skanningsskärmen.
 *
 * Hittade enheter ligger kvar när skanningen har stoppats, så att användaren kan välja bland dem.
 *
 * @property error Felet som stoppade den senaste skanningen, eller `null`.
 * @property saveDialog Dialogen för att namnge och spara en enhet, eller `null` om den är stängd.
 * @property savedDevice Sätts när en enhet har sparats. Skärmen navigerar då tillbaka.
 */
data class ScanUiState(
    val devices: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val error: BleError? = null,
    val saveDialog: SaveDialogState? = null,
    val savedDevice: Device? = null,
)

/** Dialogen där användaren väljer namn på en hittad enhet innan den sparas. */
data class SaveDialogState(
    val device: ScannedDevice,
    val name: String,
    val error: SaveDeviceError? = null,
    val isSaving: Boolean = false,
)
