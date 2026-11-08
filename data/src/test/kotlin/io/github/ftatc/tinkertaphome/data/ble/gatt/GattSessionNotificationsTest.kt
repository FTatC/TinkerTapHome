package io.github.ftatc.tinkertaphome.data.ble.gatt

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GattSessionNotificationsTest {

    private val connector = FakeGattConnector()
    private val session = GattSession("AA:BB:CC:DD:EE:01", connector)
    private val sensor = testCharacteristic(1)
    private val other = testCharacteristic(2)

    private fun changed(characteristic: CharacteristicId, vararg bytes: Byte) =
        GattEvent.CharacteristicChanged(characteristic, bytes)

    private suspend fun ReceiveTurbine<Outcome<ByteArray, ConnectionError>>.awaitValue(): ByteArray =
        (awaitItem() as Outcome.Success).value

    @Test
    fun `turns notifications on and emits each new value`() = runTest {
        session.connect()

        session.notifications(sensor).test {
            runCurrent()
            assertEquals(FakeRequest.SetNotifications(sensor, enabled = true), connector.requests.last())

            connector.emit(changed(sensor, 1))
            assertArrayEquals(byteArrayOf(1), awaitValue())
            connector.emit(changed(sensor, 2))
            assertArrayEquals(byteArrayOf(2), awaitValue())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores values from other characteristics`() = runTest {
        session.connect()

        session.notifications(sensor).test {
            runCurrent()
            connector.emit(changed(other, 9))
            connector.emit(changed(sensor, 1))

            assertArrayEquals(byteArrayOf(1), awaitValue())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not miss a value sent right after notifications are turned on`() = runTest {
        session.connect()
        connector.respond = { request ->
            connector.emit(connector.successFor(request))
            changed(sensor, 7)
        }

        session.notifications(sensor).test {
            assertArrayEquals(byteArrayOf(7), awaitValue())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turns notifications off when collection stops`() = runTest {
        session.connect()

        session.notifications(sensor).test {
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()

        assertEquals(FakeRequest.SetNotifications(sensor, enabled = false), connector.requests.last())
    }

    @Test
    fun `fails and completes when notifications cannot be turned on`() = runTest {
        session.connect()
        connector.respond = { GattEvent.NotificationsChanged(sensor, status = 3) }

        session.notifications(sensor).test {
            assertEquals(Outcome.Failure(ConnectionError.OperationFailed(3)), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `fails without a request when not connected`() = runTest {
        session.notifications(sensor).test {
            assertEquals(Outcome.Failure(ConnectionError.NotConnected), awaitItem())
            awaitComplete()
        }
        assertFalse(connector.requests.any { it is FakeRequest.SetNotifications })
    }

    @Test
    fun `fails and completes when the connection is lost`() = runTest {
        session.connect()

        session.notifications(sensor).test {
            runCurrent()
            connector.emit(GattEvent.ConnectionChanged(status = 8, connected = false))

            assertEquals(Outcome.Failure(ConnectionError.ConnectionLost), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `completes without an error when the user disconnects`() = runTest {
        session.connect()

        session.notifications(sensor).test {
            runCurrent()
            session.disconnect()

            awaitComplete()
        }
        assertFalse(FakeRequest.SetNotifications(sensor, enabled = false) in connector.requests)
    }
}
