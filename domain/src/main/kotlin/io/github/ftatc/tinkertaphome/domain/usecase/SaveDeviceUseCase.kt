package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject

/** Sparar en hittad enhet under det namn användaren har valt. Blanksteg runt namnet tas bort. */
class SaveDeviceUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(address: BleAddress, name: String): Outcome<Device, SaveDeviceError> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Outcome.Failure(SaveDeviceError.NameMissing)
        return repository.save(address, trimmed)
    }
}
