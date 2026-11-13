package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject

/**
 * Sparar en hittad enhet under det namn användaren har valt. Namnet rensas från dolda tecken och
 * överflödiga blanksteg med [DeviceNames.clean] innan det kontrolleras och sparas.
 */
class SaveDeviceUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(address: BleAddress, name: String): Outcome<Device, SaveDeviceError> {
        val cleaned = DeviceNames.clean(name)
        return when {
            cleaned.isEmpty() -> Outcome.Failure(SaveDeviceError.NameMissing)
            DeviceNames.length(cleaned) > DeviceNames.MAX_LENGTH -> Outcome.Failure(SaveDeviceError.NameTooLong)
            // Tokenet anges i spara-flödet för TinkerTapNode (steg 6.5 i docs/roadmap.md).
            else -> repository.save(address, cleaned, token = null)
        }
    }
}
