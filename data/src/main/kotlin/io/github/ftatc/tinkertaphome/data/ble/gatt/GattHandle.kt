package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId

/**
 * Tunt lager runt Androids `BluetoothGatt` för en anslutning. Innehåller ingen logik, så att
 * [GattSession] och [GattOperationQueue] kan testas med en fake i stället för den riktiga
 * Bluetooth-stacken (se CLAUDE.md).
 *
 * Varje metod startar en operation och returnerar direkt. Svaret kommer senare som en [GattEvent].
 * Inga metoder kastar: saknad behörighet och andra hinder rapporteras som [GattRequest].
 */
internal interface GattHandle {

    fun discoverServices(): GattRequest

    /** `true` om enheten redan är parkopplad med telefonen. */
    fun isBonded(): Boolean

    fun createBond(): GattRequest

    fun read(characteristic: CharacteristicId): GattRequest

    fun write(characteristic: CharacteristicId, value: ByteArray): GattRequest

    /**
     * Slår på eller av notifieringar: både lokalt i Android och genom att skriva enhetens CCCD.
     * Svaret kommer som [GattEvent.NotificationsChanged].
     */
    fun setNotifications(characteristic: CharacteristicId, enabled: Boolean): GattRequest

    /** Kopplar ner och frigör anslutningen. Efter det kommer inga fler händelser. Får inte kasta. */
    fun close()
}

/** Startar anslutningar. Skiljer ut Android-delen från logiken i [GattSession]. */
internal interface GattConnector {

    /** `true` om telefonen har Bluetooth och det är påslaget. */
    fun isBluetoothEnabled(): Boolean

    /**
     * Börjar ansluta till enheten med [address]. Alla händelser för anslutningen skickas till
     * [onEvent], från vilken tråd som helst, tills [GattHandle.close] har anropats.
     */
    fun connect(address: String, onEvent: (GattEvent) -> Unit): GattConnectStart
}

/** Resultatet av att försöka starta en anslutning. */
internal sealed interface GattConnectStart {

    /** Anslutningen har startats. Att den lyckas rapporteras som [GattEvent.ConnectionChanged]. */
    class Started(val handle: GattHandle) : GattConnectStart

    data object BluetoothOff : GattConnectStart

    data object PermissionDenied : GattConnectStart
}

/** Svaret när en operation startas. Fel vid start är data, inte undantag. */
internal enum class GattRequest {

    /** Operationen är startad. Svaret kommer som en [GattEvent]. */
    STARTED,

    /** Tjänsten eller characteristicen finns inte på enheten. */
    NOT_FOUND,

    /** Android vägrade starta operationen, till exempel för att anslutningen är stängd. */
    REJECTED,

    /** Android nekade operationen eftersom BLE-behörigheten saknas. */
    PERMISSION_DENIED,
}
