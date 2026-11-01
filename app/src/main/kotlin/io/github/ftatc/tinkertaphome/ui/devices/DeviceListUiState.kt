package io.github.ftatc.tinkertaphome.ui.devices

import io.github.ftatc.tinkertaphome.domain.model.Device

/** UI-tillståndet för enhetslistan. */
sealed interface DeviceListUiState {

    data object Loading : DeviceListUiState

    data object Empty : DeviceListUiState

    data class Content(val devices: List<Device>) : DeviceListUiState
}
