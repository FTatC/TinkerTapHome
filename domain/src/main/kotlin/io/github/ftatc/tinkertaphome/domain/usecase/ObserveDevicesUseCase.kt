package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Strömmar sparade enheter sorterade på namn, oberoende av versaler och gemener. */
class ObserveDevicesUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    operator fun invoke(): Flow<List<Device>> =
        repository.observeDevices().map { devices -> devices.sortedWith(byName) }

    private companion object {
        val byName: Comparator<Device> =
            compareBy(String.CASE_INSENSITIVE_ORDER) { device: Device -> device.name }
                .thenBy { it.id }
    }
}
