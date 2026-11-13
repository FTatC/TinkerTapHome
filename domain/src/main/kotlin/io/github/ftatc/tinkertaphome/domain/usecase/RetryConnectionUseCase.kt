package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import javax.inject.Inject

/**
 * Försöker ansluta igen efter ett fel som har stoppat [KeepSavedDevicesConnectedUseCase], till
 * exempel när användaren har slagit på Bluetooth eller parkopplat enheten igen.
 *
 * Nollställer felet genom att koppla ner. [KeepSavedDevicesConnectedUseCase] ser att felet är borta
 * och ansluter igen.
 */
class RetryConnectionUseCase @Inject constructor(
    private val connection: DeviceConnection,
) {
    operator fun invoke(address: BleAddress) {
        connection.disconnect(address)
    }
}
