package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.repository.OutputPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Strömmar namnen på de utgångar på noden som användaren vill se som tryckknappar. */
class ObservePushButtonsUseCase @Inject constructor(
    private val repository: OutputPreferencesRepository,
) {
    operator fun invoke(address: BleAddress): Flow<Outcome<Set<String>, DeviceError>> =
        repository.observePushButtons(address)
}
