package io.github.ftatc.tinkertaphome.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveDevicesUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    observeDevices: ObserveDevicesUseCase,
) : ViewModel() {

    val uiState: StateFlow<DeviceListUiState> = observeDevices()
        .map(::toUiState)
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

internal fun toUiState(devices: List<Device>): DeviceListUiState =
    if (devices.isEmpty()) DeviceListUiState.Empty else DeviceListUiState.Content(devices)
