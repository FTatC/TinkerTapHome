package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GattSessionReplyTest {

    private val connector = FakeGattConnector()
    private val session = GattSession("AA:BB:CC:DD:EE:01", connector)
    private val command = testCharacteristic(1)
    private val result = testCharacteristic(2)
    private val state = testCharacteristic(3)

    /** Godtar svar vars första byte är 0x01, som opcoden i ett TinkerTapNode-svar. */
    private val isReply: (ByteArray) -> Boolean = { it.firstOrNull() == 0x01.toByte() }

    private suspend fun connected() {
        assertEquals(Outcome.Success(Unit), session.connect())
    }

    private suspend fun send() = session.writeAndAwaitReply(command, byteArrayOf(1, 0, 1), result, isReply)

    /** Svarar på skrivningen genom att först skicka [notifications] och sedan bekräfta den, som TinkerTapNode gör. */
    private fun answerWritesWith(vararg notifications: GattEvent.CharacteristicChanged) {
        connector.respond = { request ->
            if (request is FakeRequest.Write) {
                notifications.forEach(connector::emit)
                GattEvent.CharacteristicWritten(request.characteristic, GATT_SUCCESS)
            } else {
                connector.successFor(request)
            }
        }
    }

    private fun changed(characteristic: CharacteristicId, vararg bytes: Int) =
        GattEvent.CharacteristicChanged(characteristic, ByteArray(bytes.size) { bytes[it].toByte() })

    @Test
    fun `returns the reply that the device sends before confirming the write`() = runTest {
        connected()
        answerWritesWith(changed(state, 1), changed(result, 1, 0, 0))

        val reply = send()

        assertArrayEquals(byteArrayOf(1, 0, 0), (reply as Outcome.Success).value)
        assertEquals(FakeRequest.Write(command, listOf<Byte>(1, 0, 1)), connector.requests.last())
    }

    @Test
    fun `returns a reply that comes after the write is confirmed`() = runTest {
        connected()
        val reply = async { send() }
        runCurrent()

        connector.emit(changed(result, 1, 0, 0))

        assertArrayEquals(byteArrayOf(1, 0, 0), (reply.await() as Outcome.Success).value)
    }

    @Test
    fun `ignores notifications that are not the reply`() = runTest {
        connected()
        val reply = async { send() }
        runCurrent()

        connector.emit(changed(result, 2, 0, 0))
        connector.emit(changed(state, 1, 0, 0))
        runCurrent()
        assertFalse(reply.isCompleted)

        connector.emit(changed(result, 1, 0, 0))
        assertArrayEquals(byteArrayOf(1, 0, 0), (reply.await() as Outcome.Success).value)
    }

    @Test
    fun `does not use a reply that came before the write started`() = runTest {
        // Result behåller sitt värde från förra kommandot, även från en tidigare anslutning.
        connected()
        connector.emit(changed(result, 1, 0, 0))

        val reply = async { send() }
        advanceTimeBy(GattSession.REPLY_TIMEOUT + 100.milliseconds)

        assertEquals(Outcome.Failure(ConnectionError.NoReply), reply.await())
    }

    @Test
    fun `closes the connection when no reply comes`() = runTest {
        // Noden ignorerar alla kommandon när den har bestämt sig för att koppla ner.
        connected()
        val reply = async { send() }

        advanceTimeBy(GattSession.REPLY_TIMEOUT + 100.milliseconds)

        assertEquals(Outcome.Failure(ConnectionError.NoReply), reply.await())
        assertEquals(ConnectionState.Failed(ConnectionError.NoReply), session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `fails without waiting when the write fails`() = runTest {
        connected()
        connector.respond = { request ->
            if (request is FakeRequest.Write) GattEvent.CharacteristicWritten(command, GATT_INSUFFICIENT_ENCRYPTION)
            else connector.successFor(request)
        }

        assertEquals(Outcome.Failure(ConnectionError.PairingMissing), send())
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `fails with the connection error when the connection is lost while waiting`() = runTest {
        connected()
        val reply = async { send() }
        runCurrent()

        connector.emit(GattEvent.ConnectionChanged(status = 8, connected = false))

        assertEquals(Outcome.Failure(ConnectionError.ConnectionLost), reply.await())
    }

    @Test
    fun `fails without a request when not connected`() = runTest {
        assertEquals(Outcome.Failure(ConnectionError.NotConnected), send())
        assertTrue(connector.requests.isEmpty())
    }

    @Test
    fun `runs no other operation until the reply has come`() = runTest {
        connected()
        val reply = async { send() }
        runCurrent()
        val read = async { session.read(state) }
        runCurrent()

        assertFalse(FakeRequest.Read(state) in connector.requests)

        connector.emit(changed(result, 1, 0, 0))
        reply.await()
        runCurrent()

        assertEquals(FakeRequest.Read(state), connector.requests.last())
        read.await()
    }

    @Test
    fun `turns on notifications for the reply before the first write and only once`() = runTest {
        connected()
        answerWritesWith(changed(result, 1, 0, 0))

        send()
        send()

        val requests = connector.requests.filter { it !is FakeRequest.DiscoverServices && it !is FakeRequest.RequestMtu }
        assertEquals(
            listOf(
                FakeRequest.SetNotifications(result, enabled = true),
                FakeRequest.Write(command, listOf<Byte>(1, 0, 1)),
                FakeRequest.Write(command, listOf<Byte>(1, 0, 1)),
            ),
            requests,
        )
    }

    @Test
    fun `turns on notifications again after a new connection`() = runTest {
        connected()
        answerWritesWith(changed(result, 1, 0, 0))
        send()
        session.disconnect()

        connected()
        send()

        assertEquals(2, connector.requests.count { it == FakeRequest.SetNotifications(result, enabled = true) })
    }

    @Test
    fun `does not write when notifications for the reply cannot be turned on`() = runTest {
        connected()
        connector.respond = { request ->
            if (request is FakeRequest.SetNotifications) GattEvent.NotificationsChanged(result, status = 3) else connector.successFor(request)
        }

        assertEquals(Outcome.Failure(ConnectionError.OperationFailed(3)), send())
        assertFalse(connector.requests.any { it is FakeRequest.Write })
    }
}
