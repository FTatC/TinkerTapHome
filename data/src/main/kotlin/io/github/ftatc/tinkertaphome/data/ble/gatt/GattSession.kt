package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private typealias GattOutcome<T> = Outcome<T, ConnectionError>

/**
 * Anslutningen till en enhet. Ren Kotlin: Android nås bara via [GattConnector] och [GattHandle].
 *
 * Alla operationer går genom en [GattOperationQueue], så att bara en GATT-operation är igång åt
 * gången. Händelser från Android kan komma från vilken tråd som helst och tas emot i [onEvent].
 */
internal class GattSession(
    private val address: String,
    private val connector: GattConnector,
) {

    private val queue = GattOperationQueue(onTimeout = { dropConnection(ConnectionError.Timeout) })
    private val handle = AtomicReference<GattHandle?>(null)
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Nya värden från notifieringar. Tappade värden gör inget, eftersom nästa värde ersätter dem. */
    private val changes = MutableSharedFlow<GattEvent.CharacteristicChanged>(
        extraBufferCapacity = CHANGE_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Väntar på svaret på en skrivning i [writeAndAwaitReply], eller `null`. */
    private val pendingReply = AtomicReference<PendingReply?>(null)

    /**
     * Ansluter, parkopplar vid behov, ber om en större MTU och söker efter tjänster. Lyckas direkt om
     * enheten redan är ansluten. Avbryts anropet stängs anslutningen direkt och statusen blir
     * [ConnectionState.Disconnected].
     */
    suspend fun connect(): GattOutcome<Unit> = queue.withLock {
        if (_state.value == ConnectionState.Connected) return@withLock Outcome.Success(Unit)
        _state.value = ConnectionState.Connecting
        val result = openConnectionWithRetry()
            .flatMap { bondIfNeeded() }
            .flatMap { requestMtu() }
            .flatMap { discoverServices() }
        when (result) {
            is Outcome.Success -> markConnected()
            is Outcome.Failure -> result.also { markFailed(it.error) }
        }
    }

    /** Kopplar ner. En pågående operation avslutas med [ConnectionError.NotConnected]. */
    fun disconnect() {
        val closing = handle.getAndSet(null)
        _state.value = ConnectionState.Disconnected
        failPending(ConnectionError.NotConnected)
        closing?.close()
    }

    suspend fun read(characteristic: CharacteristicId): GattOutcome<ByteArray> =
        operation(start = { it.read(characteristic).toError(characteristic) }) { event ->
            (event as? GattEvent.CharacteristicRead)
                ?.takeIf { it.characteristic == characteristic }
                ?.let { it.status.toOutcome().flatMap { _ -> Outcome.Success(it.value) } }
        }

    suspend fun write(characteristic: CharacteristicId, value: ByteArray): GattOutcome<Unit> =
        whenConnected { startWrite(characteristic, value) }

    /** Se [io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection.writeAndAwaitReply]. */
    suspend fun writeAndAwaitReply(
        characteristic: CharacteristicId,
        value: ByteArray,
        reply: CharacteristicId,
        isReply: (ByteArray) -> Boolean,
    ): GattOutcome<ByteArray> = queue.withLock {
        if (_state.value != ConnectionState.Connected) return@withLock Outcome.Failure(ConnectionError.NotConnected)
        // Registreras innan skrivningen startar, eftersom svaret kan komma innan skrivningen bekräftas.
        val waiting = PendingReply(reply, isReply)
        pendingReply.set(waiting)
        try {
            startWrite(characteristic, value).flatMap { awaitReply(waiting) }
        } finally {
            pendingReply.compareAndSet(waiting, null)
        }
    }

    /** Se [io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection.observeNotifications]. */
    fun notifications(characteristic: CharacteristicId): Flow<GattOutcome<ByteArray>> = channelFlow {
        // Startas innan notifieringarna slås på, så att inget av de första värdena missas.
        val values = launch(start = CoroutineStart.UNDISPATCHED) {
            changes.collect { if (it.characteristic == characteristic) send(Outcome.Success(it.value)) }
        }
        when (val enabled = setNotifications(characteristic, enabled = true)) {
            is Outcome.Failure -> send(enabled)
            is Outcome.Success -> try {
                awaitConnectionEnd()?.let { send(Outcome.Failure(it)) }
            } finally {
                withContext(NonCancellable) { turnOffNotificationsIfConnected(characteristic) }
            }
        }
        values.cancel()
    }

    /** Tar emot en händelse från Android. Anropas från vilken tråd som helst. */
    private fun onEvent(event: GattEvent) {
        if (queue.offer(event)) return
        when (event) {
            is GattEvent.CharacteristicChanged -> {
                pendingReply.get()?.offer(event)
                changes.tryEmit(event)
            }
            is GattEvent.ConnectionChanged -> if (!event.connected) dropConnection(ConnectionError.ConnectionLost)
            // Sena svar på operationer som redan har avslutats ignoreras.
            else -> Unit
        }
    }

    /**
     * Ansluter, och försöker igen efter en kort paus när Android svarar med [GATT_ERROR]. Felet är
     * vanligt och brukar gå över av sig självt.
     */
    private suspend fun openConnectionWithRetry(): GattOutcome<Unit> {
        repeat(CONNECT_RETRIES) {
            val result = openConnection()
            if (result != Outcome.Failure(ConnectionError.ConnectFailed(GATT_ERROR))) return result
            handle.getAndSet(null)?.close()
            pauseBeforeRetry()
            if (_state.value != ConnectionState.Connecting) return Outcome.Failure(ConnectionError.NotConnected)
        }
        return openConnection()
    }

    private suspend fun openConnection(): GattOutcome<Unit> =
        queue.await(CONNECT_TIMEOUT, start = ::startConnection, abandon = ::disconnect) { event ->
            (event as? GattEvent.ConnectionChanged)?.let {
                if (it.connected && it.status == GATT_SUCCESS) Outcome.Success(Unit)
                else Outcome.Failure(ConnectionError.ConnectFailed(it.status))
            }
        }

    /** Väntar före ett nytt anslutningsförsök. Avbryts anropet blir statusen [ConnectionState.Disconnected]. */
    private suspend fun pauseBeforeRetry() {
        try {
            delay(CONNECT_RETRY_DELAY)
        } catch (e: CancellationException) {
            disconnect()
            throw e
        }
    }

    private fun startConnection(): ConnectionError? =
        when (val started = connector.connect(address, ::onEvent)) {
            is GattConnectStart.Started -> null.also { handle.set(started.handle) }
            GattConnectStart.BluetoothOff -> ConnectionError.BluetoothOff
            GattConnectStart.PermissionDenied -> ConnectionError.PermissionMissing
        }

    /**
     * Parkopplar enheten om den inte redan är parkopplad. Android ger då enheten en stabil adress,
     * som appen använder som enhetens identitet (se beslutet i `docs/roadmap.md`, fas 1.3).
     */
    private suspend fun bondIfNeeded(): GattOutcome<Unit> {
        if (handle.get()?.isBonded() == true) return Outcome.Success(Unit)
        return queue.await(BOND_TIMEOUT, start = handleRequest { it.createBond().toError() }, abandon = ::disconnect) { event ->
            when ((event as? GattEvent.BondChanged)?.state) {
                BondState.BONDED -> Outcome.Success(Unit)
                BondState.NONE -> Outcome.Failure(ConnectionError.BondFailed)
                BondState.BONDING, null -> null
            }
        }
    }

    /**
     * Ber om [MTU], så att långa värden som TinkerTapNodes Info läses i färre paket. Protokollet
     * fungerar med standardvärdet, så ett avslag eller en lägre MTU är inget fel.
     */
    private suspend fun requestMtu(): GattOutcome<Unit> {
        val result = queue.await(OPERATION_TIMEOUT, start = handleRequest { it.requestMtu(MTU).toError() }, abandon = ::disconnect) { event ->
            (event as? GattEvent.MtuChanged)?.let { Outcome.Success(Unit) }
        }
        val refused = (result as? Outcome.Failure)?.error == ConnectionError.RequestRejected
        return if (refused) Outcome.Success(Unit) else result
    }

    private suspend fun discoverServices(): GattOutcome<Unit> =
        queue.await(OPERATION_TIMEOUT, start = handleRequest { it.discoverServices().toError() }, abandon = ::disconnect) { event ->
            (event as? GattEvent.ServicesDiscovered)?.status?.toOutcome()
        }

    private suspend fun setNotifications(characteristic: CharacteristicId, enabled: Boolean): GattOutcome<Unit> =
        operation(start = { it.setNotifications(characteristic, enabled).toError(characteristic) }) { event ->
            (event as? GattEvent.NotificationsChanged)
                ?.takeIf { it.characteristic == characteristic }
                ?.status?.toOutcome()
        }

    private suspend fun turnOffNotificationsIfConnected(characteristic: CharacteristicId) {
        if (_state.value == ConnectionState.Connected) setNotifications(characteristic, enabled = false)
    }

    /** Skriver och väntar på bekräftelsen. Måste anropas inom kön och med en öppen anslutning. */
    private suspend fun startWrite(characteristic: CharacteristicId, value: ByteArray): GattOutcome<Unit> =
        queue.await(OPERATION_TIMEOUT, start = handleRequest { it.write(characteristic, value).toError(characteristic) }) { event ->
            (event as? GattEvent.CharacteristicWritten)
                ?.takeIf { it.characteristic == characteristic }
                ?.status?.toOutcome()
        }

    /** Väntar på svaret efter en bekräftad skrivning. Utan svar stängs anslutningen, som efter en timeout. */
    private suspend fun awaitReply(waiting: PendingReply): GattOutcome<ByteArray> =
        withTimeoutOrNull(REPLY_TIMEOUT) { waiting.result.await() }
            ?: Outcome.Failure(ConnectionError.NoReply).also { dropConnection(ConnectionError.NoReply) }

    /** Väntar tills anslutningen inte längre är uppe. Returnerar felet om den bröts, eller `null` om användaren kopplade ner. */
    private suspend fun awaitConnectionEnd(): ConnectionError? =
        (_state.first { it != ConnectionState.Connected } as? ConnectionState.Failed)?.error

    /** Kör en operation som kräver att enheten är ansluten. */
    private suspend fun <T> operation(
        start: (GattHandle) -> ConnectionError?,
        respond: (GattEvent) -> GattOutcome<T>?,
    ): GattOutcome<T> = whenConnected { queue.await(OPERATION_TIMEOUT, start = handleRequest(start), respond = respond) }

    /** Kör [block] ensam i kön, om enheten är ansluten. */
    private suspend fun <T> whenConnected(block: suspend () -> GattOutcome<T>): GattOutcome<T> = queue.withLock {
        if (_state.value != ConnectionState.Connected) return@withLock Outcome.Failure(ConnectionError.NotConnected)
        block()
    }

    /** Startar en operation på den öppna anslutningen, eller ger [ConnectionError.NotConnected] om den är stängd. */
    private fun handleRequest(start: (GattHandle) -> ConnectionError?): () -> ConnectionError? = {
        val open = handle.get()
        if (open == null) ConnectionError.NotConnected else start(open)
    }

    /** Sätter status till ansluten, om inte anslutningen hann brytas under tiden. */
    private fun markConnected(): GattOutcome<Unit> =
        if (_state.compareAndSet(ConnectionState.Connecting, ConnectionState.Connected)) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure((_state.value as? ConnectionState.Failed)?.error ?: ConnectionError.NotConnected)
        }

    /** Stänger anslutningen efter ett misslyckat försök. Har användaren redan kopplat ner behålls den statusen. */
    private fun markFailed(error: ConnectionError) {
        dropConnection(error)
        _state.compareAndSet(ConnectionState.Connecting, ConnectionState.Failed(error))
    }

    /** Stänger en öppen anslutning och sätter status till [ConnectionState.Failed]. Gör inget om den redan är stängd. */
    private fun dropConnection(error: ConnectionError) {
        val closing = handle.getAndSet(null) ?: return
        _state.value = ConnectionState.Failed(error)
        failPending(error)
        closing.close()
    }

    /** Avslutar en väntande operation och ett väntande svar med [error]. */
    private fun failPending(error: ConnectionError) {
        queue.failPending(error)
        pendingReply.get()?.fail(error)
    }

    companion object {
        val CONNECT_TIMEOUT: Duration = 15.seconds

        /** Antal nya försök när Android svarar [GATT_ERROR] vid anslutning. */
        const val CONNECT_RETRIES = 2

        /** Paus före ett nytt anslutningsförsök. `docs/app-flow.md` i firmwaren rekommenderar 1–2 s. */
        val CONNECT_RETRY_DELAY: Duration = 1.5.seconds

        /** Lång, eftersom användaren kan behöva bekräfta parkopplingen i en dialog. */
        val BOND_TIMEOUT: Duration = 60.seconds

        val OPERATION_TIMEOUT: Duration = 10.seconds

        /**
         * Hur länge appen väntar på svaret efter att en skrivning har bekräftats. TinkerTapNode skickar
         * svaret innan den bekräftar skrivningen, så det ska redan ha kommit (fråga 6 i
         * `docs/open-questions.md`). Tiden ger bara marginal för att händelserna behandlas.
         */
        val REPLY_TIMEOUT: Duration = 2.seconds

        /** Den MTU som TinkerTapNodes firmware rekommenderar. */
        const val MTU = 247

        private const val CHANGE_BUFFER_SIZE = 64
    }
}

/** Ett svar som [GattSession.writeAndAwaitReply] väntar på. */
private class PendingReply(
    private val characteristic: CharacteristicId,
    private val isReply: (ByteArray) -> Boolean,
) {
    val result = CompletableDeferred<GattOutcome<ByteArray>>()

    fun offer(event: GattEvent.CharacteristicChanged) {
        if (event.characteristic == characteristic && isReply(event.value)) result.complete(Outcome.Success(event.value))
    }

    fun fail(error: ConnectionError) {
        result.complete(Outcome.Failure(error))
    }
}

/** Gör om en GATT-status till ett resultat. Saknad parkoppling eller kryptering får ett eget fel. */
private fun Int.toOutcome(): GattOutcome<Unit> = when (this) {
    GATT_SUCCESS -> Outcome.Success(Unit)
    GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION -> Outcome.Failure(ConnectionError.PairingMissing)
    else -> Outcome.Failure(ConnectionError.OperationFailed(this))
}

private fun GattRequest.toError(characteristic: CharacteristicId? = null): ConnectionError? = when (this) {
    GattRequest.STARTED -> null
    GattRequest.NOT_FOUND ->
        characteristic?.let(ConnectionError::CharacteristicNotFound) ?: ConnectionError.RequestRejected
    GattRequest.REJECTED -> ConnectionError.RequestRejected
    GattRequest.PERMISSION_DENIED -> ConnectionError.PermissionMissing
}
