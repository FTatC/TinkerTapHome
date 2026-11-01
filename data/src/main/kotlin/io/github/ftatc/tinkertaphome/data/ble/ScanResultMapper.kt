package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice

/**
 * Mappar ett skanningsresultat till domänmodellen. Returnerar `null` om adressen är ogiltig.
 * Ett tomt eller blankt namn räknas som att enheten inte annonserar något namn.
 */
internal fun RawScanEvent.Result.toScannedDevice(): ScannedDevice? =
    BleAddress.parse(address)?.let { parsed ->
        ScannedDevice(address = parsed, name = name?.trim()?.takeIf { it.isNotEmpty() }, rssi = rssi)
    }
