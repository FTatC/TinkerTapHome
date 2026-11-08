package io.github.ftatc.tinkertaphome.domain.model

/** Förväntade fel när appen ansluter till, läser från eller skriver till en enhet. */
sealed interface ConnectionError {

    /** Bluetooth är avstängt på telefonen. */
    data object BluetoothOff : ConnectionError

    /** BLE-behörigheterna saknas, till exempel för att användaren tog bort dem. */
    data object PermissionMissing : ConnectionError

    /** Operationen kräver en anslutning, men enheten är inte ansluten. */
    data object NotConnected : ConnectionError

    /** Enheten svarade inte i tid. */
    data object Timeout : ConnectionError

    /** Anslutningen bröts oväntat, till exempel för att enheten kom utom räckhåll. */
    data object ConnectionLost : ConnectionError

    /** Anslutningen kunde inte upprättas. [status] är GATT-statusen som Android rapporterade. */
    data class ConnectFailed(val status: Int) : ConnectionError

    /** Parkopplingen misslyckades eller avbröts av användaren. */
    data object BondFailed : ConnectionError

    /** Enheten har inte den tjänst eller characteristic som efterfrågades. */
    data class CharacteristicNotFound(val characteristic: CharacteristicId) : ConnectionError

    /** Android vägrade starta operationen, till exempel för att Bluetooth-stacken var upptagen. */
    data object RequestRejected : ConnectionError

    /** Enheten svarade med ett fel. [status] är GATT-statusen, till exempel 5 för otillräcklig autentisering. */
    data class OperationFailed(val status: Int) : ConnectionError
}
