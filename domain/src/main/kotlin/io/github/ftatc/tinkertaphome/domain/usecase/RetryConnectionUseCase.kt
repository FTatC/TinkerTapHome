package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import javax.inject.Inject

/**
 * Försöker ansluta igen efter ett fel som har stoppat [KeepSavedDevicesConnectedUseCase], till
 * exempel när användaren har slagit på Bluetooth, parkopplat enheten igen eller angett ett nytt token.
 *
 * Nollställer felet genom att koppla ner och nollställa sessionen. [KeepSavedDevicesConnectedUseCase]
 * ser att felet är borta och ansluter igen.
 */
class RetryConnectionUseCase @Inject constructor(
    private val connection: DeviceConnection,
    private val sessions: NodeSessions,
) {
    operator fun invoke(address: BleAddress) {
        connection.disconnect(address)
        sessions.reset(address)
    }
}
