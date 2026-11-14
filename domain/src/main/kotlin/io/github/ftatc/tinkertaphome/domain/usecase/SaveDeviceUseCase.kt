package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject

/**
 * Sparar en hittad enhet under det namn användaren har valt. Namnet rensas från dolda tecken och
 * överflödiga blanksteg med [DeviceNames.clean] innan det kontrolleras och sparas.
 */
class SaveDeviceUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    /** Sparar enheten med det rensade namnet och [token]. */
    suspend operator fun invoke(
        address: BleAddress,
        name: String,
        token: AuthToken? = null,
    ): Outcome<Device, SaveDeviceError> =
        checkName(name).flatMap { cleaned -> repository.save(address, cleaned, token) }

    /** Rensar namnet och kontrollerar det, utan att spara något. Ger det rensade namnet. */
    fun checkName(name: String): Outcome<String, SaveDeviceError> {
        val cleaned = DeviceNames.clean(name)
        return when {
            cleaned.isEmpty() -> Outcome.Failure(SaveDeviceError.NameMissing)
            DeviceNames.length(cleaned) > DeviceNames.MAX_LENGTH -> Outcome.Failure(SaveDeviceError.NameTooLong)
            else -> Outcome.Success(cleaned)
        }
    }
}
