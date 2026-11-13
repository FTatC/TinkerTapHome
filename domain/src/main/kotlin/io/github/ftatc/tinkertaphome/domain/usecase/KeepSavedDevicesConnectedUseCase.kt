package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Håller alla sparade enheter anslutna så länge anropet pågår. Används medan appen syns (fråga 5 i
 * `docs/open-questions.md`).
 *
 * Varje enhet ansluts med [DeviceConnection.connect], som väntar tills enheten kommer inom räckhåll.
 * Bryts anslutningen ansluts den igen efter [RETRY_DELAY]. Fel som kräver något av användaren, till
 * exempel en parkoppling som saknas, stoppar försöken tills felet har nollställts med
 * [RetryConnectionUseCase]. Nya och borttagna enheter följs löpande.
 *
 * När anropet avbryts kopplas alla enheter ner, så att andra telefoner kan ansluta till dem.
 */
class KeepSavedDevicesConnectedUseCase @Inject constructor(
    private val observeDevices: ObserveDevicesUseCase,
    private val connection: DeviceConnection,
) {

    suspend operator fun invoke(): Unit = coroutineScope {
        val kept = mutableMapOf<BleAddress, Job>()
        try {
            observeDevices().collect { outcome ->
                // Kan enheterna inte läsas behålls de som redan hålls anslutna.
                if (outcome is Outcome.Success) {
                    val addresses = outcome.value.map { it.address }.toSet()
                    (kept.keys - addresses).forEach { stopKeeping(it, kept) }
                    (addresses - kept.keys).forEach { kept[it] = launch { keepConnected(it) } }
                }
            }
        } finally {
            kept.keys.forEach(connection::disconnect)
        }
    }

    private fun stopKeeping(address: BleAddress, kept: MutableMap<BleAddress, Job>) {
        kept.remove(address)?.cancel()
        connection.disconnect(address)
    }

    private suspend fun keepConnected(address: BleAddress) {
        while (true) {
            val error = connectUntilEnded(address)
            when {
                error == null -> Unit // Felet nollställdes, så anslut igen direkt.
                error.needsUser() -> awaitReset(address)
                else -> delay(RETRY_DELAY)
            }
        }
    }

    /** Ansluter och väntar tills anslutningen tar slut. Returnerar felet, eller `null` om den kopplades ner utan fel. */
    private suspend fun connectUntilEnded(address: BleAddress): ConnectionError? =
        when (val result = connection.connect(address)) {
            is Outcome.Failure -> result.error
            is Outcome.Success -> (connection.observeState(address).first { it != ConnectionState.Connected } as? ConnectionState.Failed)?.error
        }

    /** Väntar tills felet har nollställts, till exempel när användaren trycker på Försök igen. */
    private suspend fun awaitReset(address: BleAddress) {
        connection.observeState(address).first { it !is ConnectionState.Failed }
    }

    companion object {
        /** Väntetid före ett nytt försök efter ett fel som kan gå över av sig självt (beslut 2026-11-13). */
        val RETRY_DELAY: Duration = 5.seconds
    }
}

/** `true` för fel som inte går över av sig självt utan kräver något av användaren. */
private fun ConnectionError.needsUser(): Boolean = when (this) {
    ConnectionError.PairingMissing,
    ConnectionError.BondFailed,
    ConnectionError.PermissionMissing,
    ConnectionError.BluetoothOff,
    is ConnectionError.CharacteristicNotFound,
    -> true
    ConnectionError.NotConnected,
    ConnectionError.Timeout,
    ConnectionError.ConnectionLost,
    is ConnectionError.ConnectFailed,
    ConnectionError.NoReply,
    ConnectionError.RequestRejected,
    is ConnectionError.OperationFailed,
    -> false
}
