package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.data.ble.protocol.NodeProtocol
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice

/**
 * Mappar ett skanningsresultat till domänmodellen. Returnerar `null` om adressen är ogiltig.
 *
 * Namnet kommer från reklampaketet, som vem som helst i närheten kan skicka. Det rensas från dolda
 * tecken och kortas till [DeviceNames.MAX_LENGTH] tecken. Blir det tomt räknas det som inget namn.
 *
 * En enhet som annonserar TinkerTapNodes service markeras som nod, eftersom bara noder kan sparas.
 */
internal fun RawScanEvent.Result.toScannedDevice(): ScannedDevice? =
    BleAddress.parse(address)?.let { parsed ->
        ScannedDevice(
            address = parsed,
            name = name?.let(::advertisedName),
            rssi = rssi,
            isTinkerTapNode = NodeProtocol.SERVICE in serviceUuids,
        )
    }

private fun advertisedName(raw: String): String? =
    DeviceNames.truncate(DeviceNames.clean(raw)).takeIf { it.isNotEmpty() }
