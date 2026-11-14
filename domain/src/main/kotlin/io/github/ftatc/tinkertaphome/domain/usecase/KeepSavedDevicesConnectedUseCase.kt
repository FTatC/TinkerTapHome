package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Håller alla sparade enheter anslutna så länge anropet pågår. Används medan appen syns (fråga 5 i
 * `docs/open-questions.md`).
 *
 * Varje enhet ansluts med [DeviceConnection.connect], som väntar tills enheten kommer inom räckhåll.
 * Direkt efter anslutningen körs sessionen med noden ([NodeSessions.run]) med nodens sparade token.
 * Bryts anslutningen ansluts den igen efter [RETRY_DELAY]. Fel som kräver något av användaren, till
 * exempel en parkoppling som saknas eller fel token, stoppar försöken tills felet har nollställts med
 * [RetryConnectionUseCase]. Appen försöker alltså aldrig igen med ett token som noden har avvisat.
 * Nya och borttagna enheter följs löpande.
 *
 * När anropet avbryts kopplas alla enheter ner, så att andra telefoner kan ansluta till dem.
 */
class KeepSavedDevicesConnectedUseCase @Inject constructor(
    private val observeDevices: ObserveDevicesUseCase,
    private val repository: DeviceRepository,
    private val connection: DeviceConnection,
    private val sessions: NodeSessions,
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
            when (connectUntilEnded(address)) {
                Ended.WITHOUT_ERROR -> Unit // Felet nollställdes, så anslut igen direkt.
                Ended.NEEDS_USER -> awaitReset(address)
                Ended.MAY_PASS -> delay(RETRY_DELAY)
            }
        }
    }

    /** Ansluter, kör sessionen med noden och väntar tills den tar slut. */
    private suspend fun connectUntilEnded(address: BleAddress): Ended {
        when (val connected = connection.connect(address)) {
            is Outcome.Failure -> return connected.error.toEnded()
            is Outcome.Success -> Unit
        }
        val token = when (val read = repository.authToken(address)) {
            is Outcome.Success -> read.value
            // Lagringen brukar gå att läsa igen, så försök senare i stället för att visa att tokenet saknas.
            is Outcome.Failure -> return Ended.MAY_PASS.also { connection.disconnect(address) }
        }
        return sessions.run(address, token)?.toEnded() ?: Ended.WITHOUT_ERROR
    }

    /** Väntar tills felet har nollställts, till exempel när användaren trycker på Försök igen. */
    private suspend fun awaitReset(address: BleAddress) {
        combine(connection.observeState(address), sessions.observe(address)) { state, session ->
            state !is ConnectionState.Failed && session !is NodeSession.Failed
        }.first { it }
    }

    companion object {
        /** Väntetid före ett nytt försök efter ett fel som kan gå över av sig självt (beslut 2026-11-13). */
        val RETRY_DELAY: Duration = 5.seconds
    }
}

/** Hur en anslutning tog slut, och därmed när nästa försök görs. */
private enum class Ended {
    /** Noden kopplades ner utan fel, till exempel när användaren nollställde ett fel. */
    WITHOUT_ERROR,

    /** Ett fel som kräver något av användaren. */
    NEEDS_USER,

    /** Ett fel som kan gå över av sig självt. */
    MAY_PASS,
}

private fun ConnectionError.toEnded(): Ended = if (needsUser()) Ended.NEEDS_USER else Ended.MAY_PASS

/**
 * Fel från sessionen. Bara anslutningsfel kan gå över av sig självt. Ett avvisat token, ett token som
 * saknas och en nod som appen inte kan använda kräver alltid något av användaren.
 */
private fun NodeSessionError.toEnded(): Ended = when (this) {
    NodeSessionError.TokenMissing -> Ended.NEEDS_USER
    is NodeSessionError.Node -> when (val failure = failure) {
        is NodeFailure.Connection -> failure.error.toEnded()
        is NodeFailure.Node, is NodeFailure.Command -> Ended.NEEDS_USER
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
