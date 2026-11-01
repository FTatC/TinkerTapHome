package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.map
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.DeviceScanner
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Skannar efter enheter som inte redan är sparade, med starkast signal först.
 *
 * Skanningen stoppas automatiskt efter [SCAN_DURATION] för att spara batteri, och flödet avslutas
 * då normalt. Android begränsar dessutom hur ofta en app får starta nya skanningar.
 */
class ScanForDevicesUseCase @Inject constructor(
    private val scanner: DeviceScanner,
    private val repository: DeviceRepository,
) {
    operator fun invoke(): Flow<Outcome<List<ScannedDevice>, BleError>> = flow {
        withTimeoutOrNull(SCAN_DURATION) {
            combine(scanner.scan(), savedAddresses()) { scan, saved ->
                scan.map { devices -> devices.filterNot { it.address in saved }.sortedWith(strongestFirst) }
            }.collect { emit(it) }
        }
    }

    /** Adresserna till sparade enheter. Går lagringen inte att läsa filtreras ingenting bort. */
    private fun savedAddresses(): Flow<Set<BleAddress>> = repository.observeDevices().map { outcome ->
        when (outcome) {
            is Outcome.Success -> outcome.value.mapTo(mutableSetOf()) { it.address }
            is Outcome.Failure -> emptySet()
        }
    }

    companion object {
        val SCAN_DURATION = 30.seconds

        private val strongestFirst: Comparator<ScannedDevice> =
            compareByDescending<ScannedDevice> { it.rssi }.thenBy { it.address.value }
    }
}
