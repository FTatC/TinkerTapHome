package io.github.ftatc.tinkertaphome.domain.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import kotlinx.coroutines.flow.Flow

/** Söker efter BLE-enheter i närheten. Implementeras i `data`-lagret. */
interface DeviceScanner {

    /**
     * Startar en skanning när flödet samlas in och stoppar den när insamlingen avbryts.
     *
     * Skickar först en tom lista, och sedan hela listan med unika enheter varje gång en enhet
     * hittas eller uppdateras. Vid fel skickas [Outcome.Failure] och flödet avslutas.
     *
     * [power] avgör hur ofta telefonen lyssnar efter enheter, och därmed hur mycket ström skanningen drar.
     */
    fun scan(power: ScanPower = ScanPower.HIGH): Flow<Outcome<List<ScannedDevice>, BleError>>
}

/** Hur intensivt skanningen lyssnar efter enheter. */
enum class ScanPower {
    /** Lyssnar hela tiden. Hittar enheter snabbast men drar mest ström. Passar korta skanningar. */
    HIGH,

    /** Lyssnar en del av tiden. Hittar enheter något långsammare men drar mindre ström. */
    BALANCED,
}
