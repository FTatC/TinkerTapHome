package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import javax.inject.Inject

/**
 * Parkopplar en sparad nod igen, efter att noden har glömt telefonen eller parkopplingen har tagits
 * bort i Android (steg 7.3 i `docs/roadmap.md`). Noden behåller sitt namn och sitt token.
 * Användaren ska ha tryckt på nodens parkopplingsknapp först.
 *
 * När parkopplingen har lyckats nollställs sessionens fel, och [KeepSavedDevicesConnectedUseCase]
 * fortsätter med att logga in på noden som vanligt.
 */
class RepairNodeUseCase @Inject constructor(
    private val connection: DeviceConnection,
    private val sessions: NodeSessions,
) {
    suspend operator fun invoke(address: BleAddress): Outcome<Unit, RepairNodeError> {
        // Tror Android att telefonen fortfarande är parkopplad hoppar den över parkopplingen, och
        // noden avvisar ändå krypteringen. Parkopplingen måste då först tas bort i Android.
        if (connection.isPaired(address)) return Outcome.Failure(RepairNodeError.StillPairedInAndroid)
        return when (val paired = connection.pair(address)) {
            is Outcome.Success -> Outcome.Success(Unit).also { sessions.reset(address) }
            is Outcome.Failure -> Outcome.Failure(RepairNodeError.Connection(paired.error))
        }
    }
}

/** Varför en sparad nod inte kunde parkopplas igen. */
sealed interface RepairNodeError {

    /** Android är fortfarande parkopplad med noden. Användaren måste ta bort noden i Androids Bluetooth-inställningar. */
    data object StillPairedInAndroid : RepairNodeError

    /** Anslutningen eller parkopplingen misslyckades, till exempel för att knappen på noden inte trycktes. */
    data class Connection(val error: ConnectionError) : RepairNodeError
}
