package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject

/**
 * Sparar ett nytt token för noden, till exempel efter `AUTH_FAILED`, och ansluter igen med det
 * (fråga 2 i `docs/open-questions.md`). Kan tokenet inte sparas ansluter appen inte igen.
 */
class UpdateAuthTokenUseCase @Inject constructor(
    private val repository: DeviceRepository,
    private val retryConnection: RetryConnectionUseCase,
) {
    suspend operator fun invoke(address: BleAddress, token: AuthToken): Outcome<Unit, DeviceError> {
        val saved = repository.updateAuthToken(address, token)
        if (saved is Outcome.Success) retryConnection(address)
        return saved
    }
}
