package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.domain.repository.OutputPreferencesRepository
import javax.inject.Inject

/**
 * Sparar om en digital utgång ska visas som tryckknapp, som skickar en puls, eller som switch.
 * Bara digitala utgångar kan vara tryckknappar. Att anropa med en annan typ är ett programmeringsfel.
 */
class SetPushButtonUseCase @Inject constructor(
    private val repository: OutputPreferencesRepository,
) {
    suspend operator fun invoke(address: BleAddress, output: Output, pushButton: Boolean): Outcome<Unit, DeviceError> {
        require(output.type == OutputType.Digital) { "Bara digitala utgångar kan vara tryckknappar: ${output.type}" }
        return repository.setPushButton(address, output.name, pushButton)
    }
}
