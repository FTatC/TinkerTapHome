package io.github.ftatc.tinkertaphome.data.ble

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** Tar emot callbacks från en skanner. Android-delen översätter `ScanCallback` till det här. */
internal interface ScanListener {
    fun onResult(result: RawScanEvent.Result)
    fun onFailed(errorCode: Int)
}

/** Resultatet av att försöka starta en skanning. Fel vid start är data, inte undantag. */
internal sealed interface ScanStart {

    /** Skanningen har startat. [stop] stoppar den och får inte kasta. */
    class Started(val stop: () -> Unit) : ScanStart

    data object Unavailable : ScanStart

    data object PermissionDenied : ScanStart
}

/**
 * Gör om en callback-baserad skanner till ett [Flow]. Ren Kotlin, så att kanallogiken kan testas
 * utan Android. [start] anropas när flödet samlas in, och `stop` när insamlingen avbryts.
 *
 * Bufferten tar bort de äldsta händelserna när den är full, så `trySend` från en callback lyckas
 * alltid. Ett fel skickas alltid sist innan flödet stängs och är därför alltid nyast, så det kan
 * aldrig tas bort. Tappade resultat gör inget: enheter annonserar om och om igen.
 */
internal fun scanEventFlow(start: (ScanListener) -> ScanStart): Flow<RawScanEvent> = callbackFlow {
    val listener = object : ScanListener {
        override fun onResult(result: RawScanEvent.Result) {
            trySend(result)
        }

        override fun onFailed(errorCode: Int) {
            trySend(RawScanEvent.Failed(errorCode))
            close()
        }
    }
    when (val started = start(listener)) {
        is ScanStart.Started -> awaitClose { started.stop() }
        ScanStart.Unavailable -> stopWith(RawScanEvent.Unavailable)
        ScanStart.PermissionDenied -> stopWith(RawScanEvent.PermissionDenied)
    }
}.buffer(capacity = EVENT_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

private const val EVENT_BUFFER_SIZE = 64

private suspend fun ProducerScope<RawScanEvent>.stopWith(event: RawScanEvent) {
    // send väntar tills det finns plats, så händelsen kan inte tappas.
    send(event)
    close()
}
