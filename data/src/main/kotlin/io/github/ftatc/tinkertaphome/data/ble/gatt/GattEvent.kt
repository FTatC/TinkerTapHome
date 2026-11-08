package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId

/** En händelse från Androids GATT-callbacks, med vanliga datatyper i stället för Android-klasser. */
internal sealed interface GattEvent {

    /** Anslutningen upprättades eller bröts. [status] är [GATT_SUCCESS] om inget fel inträffade. */
    data class ConnectionChanged(val status: Int, val connected: Boolean) : GattEvent

    data class ServicesDiscovered(val status: Int) : GattEvent

    /** Parkopplingen ändrades. Kommer från Android-systemet och inte från GATT-anslutningen. */
    data class BondChanged(val state: BondState) : GattEvent

    class CharacteristicRead(val characteristic: CharacteristicId, val status: Int, val value: ByteArray) : GattEvent

    data class CharacteristicWritten(val characteristic: CharacteristicId, val status: Int) : GattEvent

    /** Enhetens CCCD har skrivits, så notifieringarna är på eller av. */
    data class NotificationsChanged(val characteristic: CharacteristicId, val status: Int) : GattEvent

    /** Enheten skickade ett nytt värde via en notifiering eller indikering. */
    class CharacteristicChanged(val characteristic: CharacteristicId, val value: ByteArray) : GattEvent
}

internal enum class BondState { NONE, BONDING, BONDED }

/** Androids `BluetoothGatt.GATT_SUCCESS`. Kopierad hit så att logiken kan testas utan Android. */
internal const val GATT_SUCCESS = 0
