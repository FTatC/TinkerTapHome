package io.github.ftatc.tinkertaphome.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    observeConnectedDevices: ObserveConnectedDevicesUseCase,
) : ViewModel() {

    val uiState: StateFlow<DeviceListUiState> = observeConnectedDevices()
        .map { outcome -> outcome.toDeviceListUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DeviceListUiState.Loading,
        )

    private companion object {
        /** Håller flödet vid liv över konfigurationsändringar, till exempel rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun Outcome<List<ConnectedDevice>, DeviceError>.toDeviceListUiState(): DeviceListUiState = when (this) {
    is Outcome.Success -> if (value.isEmpty()) DeviceListUiState.Empty else DeviceListUiState.Content(value)
    is Outcome.Failure -> DeviceListUiState.Error(error)
}
