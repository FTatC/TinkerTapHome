package io.github.ftatc.tinkertaphome.ui.device

import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError

/** UI-tillståndet för detaljskärmen för en sparad enhet. */
sealed interface DeviceDetailUiState {

    data object Loading : DeviceDetailUiState

    /** Enheten finns inte, till exempel för att den har tagits bort. */
    data object NotFound : DeviceDetailUiState

    data class Error(val error: DeviceError) : DeviceDetailUiState

    /**
     * @property connectError Felet från det senaste försöket att ansluta, om det misslyckades innan
     * en anslutning startades, till exempel för att Bluetooth är avstängt. Ändrar inte [connection].
     */
    data class Content(
        val device: Device,
        val connection: ConnectionState,
        val connectError: ConnectionError? = null,
    ) : DeviceDetailUiState {

        /** Felet som ska visas: från det senaste försöket att ansluta, annars från statusen. */
        val error: ConnectionError?
            get() = connectError ?: (connection as? ConnectionState.Failed)?.error
    }
}
