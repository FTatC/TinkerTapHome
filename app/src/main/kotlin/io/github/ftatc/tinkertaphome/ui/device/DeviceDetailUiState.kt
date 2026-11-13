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

    data class Content(val device: Device, val connection: ConnectionState) : DeviceDetailUiState {

        /** Felet som stoppade den senaste anslutningen, om den misslyckades. */
        val error: ConnectionError?
            get() = (connection as? ConnectionState.Failed)?.error

        /** Användaren kan försöka igen när ett fel har stoppat anslutningen. */
        val canRetry: Boolean
            get() = connection is ConnectionState.Failed
    }
}
