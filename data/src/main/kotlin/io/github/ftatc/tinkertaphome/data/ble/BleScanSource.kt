package io.github.ftatc.tinkertaphome.data.ble

import kotlinx.coroutines.flow.Flow

/**
 * Tunt lager runt Androids BLE-skanner. Ger råa händelser utan logik, så att [AndroidDeviceScanner]
 * kan testas med en fake i stället för den riktiga Bluetooth-stacken (se CLAUDE.md).
 */
internal interface BleScanSource {

    /** `true` om telefonen har Bluetooth och det är påslaget. */
    fun isBluetoothEnabled(): Boolean

    /** Startar en skanning när flödet samlas in och stoppar den när insamlingen avbryts. */
    fun scanEvents(): Flow<RawScanEvent>
}

/** En händelse från Androids skanner, med vanliga datatyper i stället för Android-klasser. */
internal sealed interface RawScanEvent {

    data class Result(val address: String, val name: String?, val rssi: Int) : RawScanEvent

    /** Händelser som avslutar skanningen. Efter en sådan kommer inga fler händelser. */
    sealed interface Stopped : RawScanEvent

    /** Bluetooth är avstängt, eller stängdes av precis när skanningen skulle starta. */
    data object BluetoothOff : Stopped

    /** Android nekade skanningen eftersom BLE-behörigheten saknas. */
    data object PermissionDenied : Stopped

    /** Skannern rapporterade ett fel. [errorCode] är ett av `ScanCallback.SCAN_FAILED_*`. */
    data class Failed(val errorCode: Int) : Stopped
}
