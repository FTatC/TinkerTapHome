package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Strömmar sessionen med noden på [BleAddress]: utgångarna med deras värden från State, och om noden
 * tar emot kommandon just nu. När noden inte är ansluten finns senast kända värden kvar.
 */
class ObserveOutputsUseCase @Inject constructor(
    private val sessions: NodeSessions,
) {
    operator fun invoke(address: BleAddress): Flow<NodeSession> = sessions.observe(address)
}
