package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.map
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Strömmar sparade enheter sorterade på namn, oberoende av versaler och gemener. Fel skickas vidare. */
class ObserveDevicesUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    operator fun invoke(): Flow<Outcome<List<Device>, DeviceError>> =
        repository.observeDevices().map { outcome -> outcome.map { devices -> devices.sortedWith(byName) } }

    private companion object {
        val byName: Comparator<Device> =
            compareBy(String.CASE_INSENSITIVE_ORDER) { device: Device -> device.name }
                .thenBy { it.id }
    }
}
