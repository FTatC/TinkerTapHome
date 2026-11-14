package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.mapError
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.NodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import javax.inject.Inject

/**
 * Andra steget när en TinkerTapNode sparas, efter [PairNodeUseCase]: kontrollerar namnet, skickar
 * `AUTH` med tokenet om noden kräver det och sparar sedan noden med tokenet (fråga 2 i
 * `docs/open-questions.md`).
 *
 * Noden kopplar ner om `AUTH` inte kommer inom 10 s, och användaren hinner ofta inte fylla i namn och
 * token så fort. Därför ansluter use caset igen innan `AUTH` skickas. Telefonen är redan parkopplad,
 * så det går snabbt. Efter `AUTH_FAILED` försöker use caset aldrig igen på egen hand.
 */
class SaveNodeUseCase @Inject constructor(
    private val saveDevice: SaveDeviceUseCase,
    private val connection: DeviceConnection,
    private val nodeClient: NodeClient,
) {
    suspend operator fun invoke(
        address: BleAddress,
        name: String,
        info: NodeInfo,
        token: AuthToken?,
    ): Outcome<Device, SaveNodeError> {
        val cleanedName = when (val checked = saveDevice.checkName(name)) {
            is Outcome.Success -> checked.value
            is Outcome.Failure -> return Outcome.Failure(SaveNodeError.Save(checked.error))
        }
        val savedToken = if (info.authRequired) token ?: return Outcome.Failure(SaveNodeError.TokenMissing) else null
        val result = authenticate(address, savedToken)
            .flatMap { saveDevice(address, cleanedName, savedToken).mapError { SaveNodeError.Save(it) } }
        if (result is Outcome.Failure) connection.disconnect(address)
        return result
    }

    private suspend fun authenticate(address: BleAddress, token: AuthToken?): Outcome<Unit, SaveNodeError> {
        if (token == null) return Outcome.Success(Unit)
        return connection.pair(address)
            .mapError<Unit, _, NodeFailure> { NodeFailure.Connection(it) }
            .flatMap { nodeClient.send(address, NodeCommand.Authenticate(token)) }
            .mapError { SaveNodeError.Node(it) }
    }
}

/** Varför en TinkerTapNode inte kunde sparas. */
sealed interface SaveNodeError {

    /** Namnet var ogiltigt, noden var redan sparad eller lagringen gick inte att skriva till. */
    data class Save(val error: SaveDeviceError) : SaveNodeError

    /** Noden kräver ett token, men inget angavs. */
    data object TokenMissing : SaveNodeError

    /** Anslutningen eller `AUTH` misslyckades, till exempel med fel token. */
    data class Node(val failure: NodeFailure) : SaveNodeError
}
