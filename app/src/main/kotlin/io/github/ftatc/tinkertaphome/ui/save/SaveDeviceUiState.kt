package io.github.ftatc.tinkertaphome.ui.save

import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError

/**
 * UI-tillståndet för dialogen som sparar en hittad enhet.
 *
 * @property saveError Felet från det senaste försöket att spara, eller `null`.
 * @property isSaved `true` när enheten är sparad. Dialogen stänger sig då och går tillbaka till listan.
 */
data class SaveDeviceUiState(
    val address: BleAddress,
    val name: String,
    val saveError: SaveDeviceError? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)
