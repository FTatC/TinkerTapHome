package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Kör GATT-operationer en i taget. Android klarar inte parallella GATT-anrop: startas en ny
 * operation innan den förra har fått svar, vägrar Android eller tappar ett av svaren.
 *
 * En operation startas med [await] inom [withLock], och svaret kommer senare via [offer] från
 * Androids callback-tråd. Kön släpps inte förrän svaret har kommit, tiden har gått ut eller
 * operationen har avbrutits med [failPending]. Det gäller även om den som väntar avbryts: annars
 * kunde ett sent svar tas för svaret på nästa operation.
 *
 * @param onTimeout Anropas när en operation aldrig fick svar. Då vet ingen vad Bluetooth-stacken
 * håller på med, så anslutningen ska stängas.
 */
internal class GattOperationQueue(private val onTimeout: () -> Unit) {

    private val mutex = Mutex()
    private val pending = AtomicReference<PendingOperation<*>?>(null)

    /** Kör [block] ensam i kön. Flera operationer i följd, till exempel en hel anslutning, kan köras i samma block. */
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Startar en operation med [start] och väntar på svaret. Måste anropas inom [withLock].
     *
     * @param start Startar operationen och returnerar `null`, eller ett fel om den inte gick att starta.
     * @param respond Gör om en händelse till resultatet, eller returnerar `null` om händelsen inte är svaret.
     * @param abandon Anropas om den som väntar avbryts. Kan avsluta operationen direkt, till exempel
     * genom att stänga anslutningen och anropa [failPending]. Annars väntar kön ut svaret.
     */
    suspend fun <T> await(
        timeout: Duration,
        start: () -> ConnectionError?,
        abandon: () -> Unit = {},
        respond: (GattEvent) -> Outcome<T, ConnectionError>?,
    ): Outcome<T, ConnectionError> {
        check(mutex.isLocked) { "await måste anropas inom withLock" }
        val operation = PendingOperation(respond)
        // Sätts innan operationen startas, eftersom svaret kan komma innan start() har returnerat.
        pending.set(operation)
        try {
            start()?.let { return Outcome.Failure(it) }
            return awaitResponse(operation, timeout, abandon)
        } finally {
            pending.compareAndSet(operation, null)
        }
    }

    /** Ger [event] till den väntande operationen. Returnerar `true` om händelsen var svaret på den. */
    fun offer(event: GattEvent): Boolean = pending.get()?.offer(event) == true

    /** Avslutar den väntande operationen med [error], till exempel när anslutningen bryts. */
    fun failPending(error: ConnectionError) {
        pending.get()?.fail(error)
    }

    private suspend fun <T> awaitResponse(
        operation: PendingOperation<T>,
        timeout: Duration,
        abandon: () -> Unit,
    ): Outcome<T, ConnectionError> {
        val result = try {
            withTimeoutOrNull(timeout) { operation.result.await() }
        } catch (e: CancellationException) {
            abandon()
            // Android kör operationen ändå, om inte abandon har avslutat den. Vänta ut svaret innan kön släpps.
            withContext(NonCancellable) { withTimeoutOrNull(timeout) { operation.result.await() } } ?: onTimeout()
            throw e
        }
        return result ?: Outcome.Failure(ConnectionError.Timeout).also { onTimeout() }
    }
}

private class PendingOperation<T>(private val respond: (GattEvent) -> Outcome<T, ConnectionError>?) {

    val result = CompletableDeferred<Outcome<T, ConnectionError>>()

    fun offer(event: GattEvent): Boolean {
        val response = respond(event) ?: return false
        return result.complete(response)
    }

    fun fail(error: ConnectionError) {
        result.complete(Outcome.Failure(error))
    }
}
