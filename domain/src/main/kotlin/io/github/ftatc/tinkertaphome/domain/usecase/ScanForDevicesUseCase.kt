package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceScanner
import io.github.ftatc.tinkertaphome.domain.map
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import io.github.ftatc.tinkertaphome.domain.takeUntilFailure
import javax.inject.Inject
import kotlin.time.Duration
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
 * då normalt. Vid fel skickas felet och flödet avslutas direkt.
 */
class ScanForDevicesUseCase @Inject constructor(
    private val scanner: DeviceScanner,
    private val repository: DeviceRepository,
) {
    operator fun invoke(): Flow<Outcome<List<ScannedDevice>, BleError>> =
        combine(scanner.scan(), savedAddresses()) { scan, saved -> scan.map { it.newDevicesStrongestFirst(saved) } }
            // Utan detta skulle combine fortsätta lyssna på de sparade enheterna efter ett fel.
            .takeUntilFailure()
            .stopAfter(SCAN_DURATION)

    /** Adresserna till sparade enheter. Går lagringen inte att läsa filtreras ingenting bort. */
    private fun savedAddresses(): Flow<Set<BleAddress>> = repository.observeDevices().map { outcome ->
        when (outcome) {
            is Outcome.Success -> outcome.value.mapTo(mutableSetOf()) { it.address }
            is Outcome.Failure -> emptySet()
        }
    }

    companion object {
        val SCAN_DURATION = 30.seconds
    }
}

private fun List<ScannedDevice>.newDevicesStrongestFirst(saved: Set<BleAddress>): List<ScannedDevice> =
    filterNot { it.address in saved }.sortedWith(compareByDescending<ScannedDevice> { it.rssi }.thenBy { it.address.value })

/** Avslutar flödet normalt efter [duration]. Att tiden tar slut räknas inte som ett fel. */
private fun <T> Flow<T>.stopAfter(duration: Duration): Flow<T> = flow {
    withTimeoutOrNull(duration) { collect { emit(it) } }
}
