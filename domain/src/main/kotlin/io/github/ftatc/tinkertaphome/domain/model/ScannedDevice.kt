package io.github.ftatc.tinkertaphome.domain.model

/**
 * En BLE-enhet som hittades vid en skanning men inte nödvändigtvis är sparad.
 *
 * @property name Namnet som enheten annonserar, eller `null` om den inte skickar något.
 * @property rssi Signalstyrka i dBm. Närmare noll betyder starkare signal.
 */
data class ScannedDevice(
    val address: BleAddress,
    val name: String?,
    val rssi: Int,
)
