package io.github.ftatc.tinkertaphome.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.ui.navigation.DeviceDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel för detaljskärmen. Visar enhetens anslutningsstatus och ansluter eller kopplar ner.
 *
 * Anslutningen ligger kvar när användaren lämnar skärmen, så att statusen syns i enhetslistan.
 * Lämnar användaren skärmen medan en anslutning pågår avbryts den.
 */
@HiltViewModel(assistedFactory = DeviceDetailViewModel.Factory::class)
class DeviceDetailViewModel @AssistedInject constructor(
    @Assisted route: DeviceDetailRoute,
    observeConnectedDevices: ObserveConnectedDevicesUseCase,
    private val connection: DeviceConnection,
) : ViewModel() {

    private val connectError = MutableStateFlow<ConnectionError?>(null)

    val uiState: StateFlow<DeviceDetailUiState> =
        combine(observeConnectedDevices.device(route.deviceId), connectError) { outcome, error ->
            outcome.toDeviceDetailUiState(error)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DeviceDetailUiState.Loading,
        )

    fun onConnectClick() {
        val device = (uiState.value as? DeviceDetailUiState.Content)?.device ?: return
        connectError.value = null
        viewModelScope.launch {
            val result = connection.connect(device.address)
            if (result is Outcome.Failure) connectError.value = result.error
        }
    }

    fun onDisconnectClick() {
        val device = (uiState.value as? DeviceDetailUiState.Content)?.device ?: return
        connectError.value = null
        connection.disconnect(device.address)
    }

    @AssistedFactory
    interface Factory {
        fun create(route: DeviceDetailRoute): DeviceDetailViewModel
    }

    private companion object {
        /** Håller flödet vid liv över konfigurationsändringar, till exempel rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun Outcome<ConnectedDevice?, DeviceError>.toDeviceDetailUiState(
    connectError: ConnectionError?,
): DeviceDetailUiState = when (this) {
    is Outcome.Failure -> DeviceDetailUiState.Error(error)
    is Outcome.Success -> value
        ?.let { DeviceDetailUiState.Content(it.device, it.connection, connectError) }
        ?: DeviceDetailUiState.NotFound
}
