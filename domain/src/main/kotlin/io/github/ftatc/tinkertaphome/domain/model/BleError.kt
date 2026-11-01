package io.github.ftatc.tinkertaphome.domain.model

/** Förväntade fel vid BLE-kommunikation. */
sealed interface BleError {

    /** Bluetooth är avstängt på telefonen. */
    data object BluetoothOff : BleError

    /** BLE-behörigheterna saknas, till exempel för att användaren tog bort dem under en skanning. */
    data object PermissionMissing : BleError

    /** Androids skanner rapporterade ett fel. [code] är ett av `ScanCallback.SCAN_FAILED_*`. */
    data class ScanFailed(val code: Int) : BleError
}
