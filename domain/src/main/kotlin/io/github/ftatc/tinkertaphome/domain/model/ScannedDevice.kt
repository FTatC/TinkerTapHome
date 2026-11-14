package io.github.ftatc.tinkertaphome.domain.model

/**
 * En BLE-enhet som hittades vid en skanning men inte nödvändigtvis är sparad.
 *
 * @property name Namnet som enheten annonserar, eller `null` om den inte skickar något.
 * @property rssi Signalstyrka i dBm. Närmare noll betyder starkare signal.
 * @property isTinkerTapNode `true` om enheten annonserar TinkerTapNodes service. Bara sådana enheter
 * kan sparas (beslut i `docs/roadmap.md`, fas 6).
 */
data class ScannedDevice(
    val address: BleAddress,
    val name: String?,
    val rssi: Int,
    val isTinkerTapNode: Boolean = false,
)
