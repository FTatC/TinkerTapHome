package io.github.ftatc.tinkertaphome.domain.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Fake för tester. Skicka resultat med [emit] och se om skanningen pågår via [isScanning]. */
class FakeDeviceScanner : DeviceScanner {

    private val results = MutableSharedFlow<Outcome<List<ScannedDevice>, BleError>>(replay = 1)

    var isScanning: Boolean = false
        private set

    suspend fun emit(outcome: Outcome<List<ScannedDevice>, BleError>) = results.emit(outcome)

    override fun scan(): Flow<Outcome<List<ScannedDevice>, BleError>> = results
        .onStart { isScanning = true }
        .onCompletion { isScanning = false }
}
