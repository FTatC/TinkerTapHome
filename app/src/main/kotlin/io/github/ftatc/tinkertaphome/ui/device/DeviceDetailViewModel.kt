package io.github.ftatc.tinkertaphome.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.RetryConnectionUseCase
import io.github.ftatc.tinkertaphome.ui.navigation.DeviceDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel för detaljskärmen. Visar enhetens anslutningsstatus.
 *
 * Appen ansluter själv till sparade enheter medan den syns (se `ForegroundConnectionsViewModel`),
 * så skärmen har inga knappar för att ansluta eller koppla ner. Har ett fel stoppat anslutningen kan
 * användaren försöka igen.
 */
@HiltViewModel(assistedFactory = DeviceDetailViewModel.Factory::class)
class DeviceDetailViewModel @AssistedInject constructor(
    @Assisted route: DeviceDetailRoute,
    observeConnectedDevices: ObserveConnectedDevicesUseCase,
    private val retryConnection: RetryConnectionUseCase,
) : ViewModel() {

    val uiState: StateFlow<DeviceDetailUiState> =
        observeConnectedDevices.device(route.deviceId)
            .map { it.toDeviceDetailUiState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DeviceDetailUiState.Loading,
            )

    fun onRetryClick() {
        val device = (uiState.value as? DeviceDetailUiState.Content)?.device ?: return
        retryConnection(device.address)
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

private fun Outcome<ConnectedDevice?, DeviceError>.toDeviceDetailUiState(): DeviceDetailUiState = when (this) {
    is Outcome.Failure -> DeviceDetailUiState.Error(error)
    is Outcome.Success -> value
        ?.let { DeviceDetailUiState.Content(it.device, it.connection) }
        ?: DeviceDetailUiState.NotFound
}
