package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import javax.inject.Inject

/**
 * Sätter alla utgångar på noden till sina säkra värden med `ALL_OFF`. Att koppla ner är inget säkert
 * läge, eftersom noden behåller sina värden (se `docs/ble-protocol.md`).
 */
class AllOffUseCase @Inject constructor(
    private val sessions: NodeSessions,
) {
    suspend operator fun invoke(address: BleAddress): Outcome<Unit, NodeFailure> =
        sessions.send(address, NodeCommand.AllOff)
}
