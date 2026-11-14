package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceScanner
import io.github.ftatc.tinkertaphome.domain.map
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.SettingsRepository
import io.github.ftatc.tinkertaphome.domain.takeUntilFailure
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Skannar efter enheter som inte redan är sparade, med starkast signal först.
 *
 * Skanningen stoppas automatiskt efter den längd som användaren har valt ([AppSettings.scanDuration])
 * för att spara batteri, och flödet avslutas då normalt. Har användaren valt att bara visa noder
 * filtreras andra enheter bort. Inställningarna läses när skanningen startar. Vid fel skickas felet
 * och flödet avslutas direkt.
 */
class ScanForDevicesUseCase @Inject constructor(
    private val scanner: DeviceScanner,
    private val repository: DeviceRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<Outcome<List<ScannedDevice>, BleError>> = flow {
        emitAll(scan(settings.observeSettings().first()))
    }

    private fun scan(settings: AppSettings): Flow<Outcome<List<ScannedDevice>, BleError>> =
        combine(scanner.scan(), savedAddresses()) { scan, saved ->
            scan.map { it.newDevicesStrongestFirst(saved).onlyNodesIf(settings.onlyNodesInScan) }
        }
            // Utan detta skulle combine fortsätta lyssna på de sparade enheterna efter ett fel.
            .takeUntilFailure()
            .stopAfter(settings.scanDuration.duration)

    /** Adresserna till sparade enheter. Går lagringen inte att läsa filtreras ingenting bort. */
    private fun savedAddresses(): Flow<Set<BleAddress>> = repository.observeDevices().map { outcome ->
        when (outcome) {
            is Outcome.Success -> outcome.value.mapTo(mutableSetOf()) { it.address }
            is Outcome.Failure -> emptySet()
        }
    }
}

private fun List<ScannedDevice>.newDevicesStrongestFirst(saved: Set<BleAddress>): List<ScannedDevice> =
    filterNot { it.address in saved }.sortedWith(compareByDescending<ScannedDevice> { it.rssi }.thenBy { it.address.value })

private fun List<ScannedDevice>.onlyNodesIf(onlyNodes: Boolean): List<ScannedDevice> =
    if (onlyNodes) filter { it.isTinkerTapNode } else this

/** Avslutar flödet normalt efter [duration]. Att tiden tar slut räknas inte som ett fel. */
private fun <T> Flow<T>.stopAfter(duration: Duration): Flow<T> = flow {
    withTimeoutOrNull(duration) { collect { emit(it) } }
}
