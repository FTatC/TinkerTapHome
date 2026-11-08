package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GattSessionTest {

    private val connector = FakeGattConnector()
    private val session = GattSession("AA:BB:CC:DD:EE:01", connector)
    private val lamp = testCharacteristic(1)
    private val other = testCharacteristic(2)

    private suspend fun connected(): GattSession = session.also {
        assertEquals(Outcome.Success(Unit), it.connect())
    }

    /** Låter testet svara själv på operationerna i stället för att fejken svarar direkt. */
    private fun respondManually() {
        connector.respond = { null }
    }

    private fun failure(error: ConnectionError) = Outcome.Failure(error)

    // Anslutning

    @Test
    fun `connects and discovers services when the device is already bonded`() = runTest {
        assertEquals(Outcome.Success(Unit), session.connect())

        assertEquals(ConnectionState.Connected, session.state.value)
        assertEquals(listOf(FakeRequest.DiscoverServices), connector.requests)
    }

    @Test
    fun `bonds before discovering services when the device is not bonded`() = runTest {
        connector.bonded = false

        assertEquals(Outcome.Success(Unit), session.connect())

        assertEquals(listOf(FakeRequest.CreateBond, FakeRequest.DiscoverServices), connector.requests)
    }

    @Test
    fun `is connecting until the device answers`() = runTest {
        connector.connectResponse = null
        val result = async { session.connect() }
        runCurrent()

        assertEquals(ConnectionState.Connecting, session.state.value)

        connector.emit(GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true))
        assertEquals(Outcome.Success(Unit), result.await())
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `succeeds without a new connection when already connected`() = runTest {
        connected()

        assertEquals(Outcome.Success(Unit), session.connect())

        assertEquals(1, connector.connectCount)
    }

    @Test
    fun `fails with the gatt status and closes when the connection cannot be made`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = 133, connected = false)

        assertEquals(failure(ConnectionError.ConnectFailed(133)), session.connect())

        assertEquals(ConnectionState.Failed(ConnectionError.ConnectFailed(133)), session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `times out and closes when the device never answers`() = runTest {
        connector.connectResponse = null
        val result = async { session.connect() }

        advanceTimeBy(GattSession.CONNECT_TIMEOUT + 1.seconds)

        assertEquals(failure(ConnectionError.Timeout), result.await())
        assertEquals(ConnectionState.Failed(ConnectionError.Timeout), session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `fails when bluetooth is turned off just before connecting`() = runTest {
        connector.refuseWith = GattConnectStart.BluetoothOff

        assertEquals(failure(ConnectionError.BluetoothOff), session.connect())
        assertEquals(ConnectionState.Failed(ConnectionError.BluetoothOff), session.state.value)
    }

    @Test
    fun `fails when the permission is missing`() = runTest {
        connector.refuseWith = GattConnectStart.PermissionDenied

        assertEquals(failure(ConnectionError.PermissionMissing), session.connect())
    }

    @Test
    fun `fails and closes when bonding is rejected`() = runTest {
        connector.bonded = false
        connector.respond = { request ->
            if (request == FakeRequest.CreateBond) GattEvent.BondChanged(BondState.NONE) else connector.successFor(request)
        }

        assertEquals(failure(ConnectionError.BondFailed), session.connect())

        assertTrue(connector.handle.closed)
        assertFalse(FakeRequest.DiscoverServices in connector.requests)
    }

    @Test
    fun `keeps waiting while bonding is in progress`() = runTest {
        connector.bonded = false
        connector.respond = { request ->
            if (request == FakeRequest.CreateBond) GattEvent.BondChanged(BondState.BONDING) else connector.successFor(request)
        }
        val result = async { session.connect() }
        runCurrent()

        assertEquals(ConnectionState.Connecting, session.state.value)

        connector.emit(GattEvent.BondChanged(BondState.BONDED))
        assertEquals(Outcome.Success(Unit), result.await())
    }

    @Test
    fun `fails with the gatt status when service discovery fails`() = runTest {
        connector.respond = { GattEvent.ServicesDiscovered(status = 129) }

        assertEquals(failure(ConnectionError.OperationFailed(129)), session.connect())
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `can connect again after a failure`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = 133, connected = false)
        session.connect()
        connector.connectResponse = GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true)

        assertEquals(Outcome.Success(Unit), session.connect())

        assertEquals(2, connector.connectCount)
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    // Läsning och skrivning

    @Test
    fun `read returns the value from the device`() = runTest {
        connected()
        connector.readValue = byteArrayOf(0x2A)

        val result = session.read(lamp)

        assertArrayEquals(byteArrayOf(0x2A), (result as Outcome.Success).value)
        assertEquals(FakeRequest.Read(lamp), connector.requests.last())
    }

    @Test
    fun `read fails with the gatt status from the device`() = runTest {
        connected()
        connector.respond = { GattEvent.CharacteristicRead(lamp, status = 5, value = byteArrayOf()) }

        assertEquals(failure(ConnectionError.OperationFailed(5)), session.read(lamp))
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `read fails when the characteristic does not exist`() = runTest {
        connected()
        connector.requestResult = { GattRequest.NOT_FOUND }

        assertEquals(failure(ConnectionError.CharacteristicNotFound(lamp)), session.read(lamp))
    }

    @Test
    fun `read fails when android rejects the request or the permission is gone`() = runTest {
        connected()

        connector.requestResult = { GattRequest.REJECTED }
        assertEquals(failure(ConnectionError.RequestRejected), session.read(lamp))

        connector.requestResult = { GattRequest.PERMISSION_DENIED }
        assertEquals(failure(ConnectionError.PermissionMissing), session.read(lamp))
    }

    @Test
    fun `read fails without a request when not connected`() = runTest {
        assertEquals(failure(ConnectionError.NotConnected), session.read(lamp))
        assertTrue(connector.requests.isEmpty())
    }

    @Test
    fun `write sends the value and waits for the device to confirm`() = runTest {
        connected()
        respondManually()
        val result = async { session.write(lamp, byteArrayOf(1)) }
        runCurrent()

        assertEquals(FakeRequest.Write(lamp, listOf<Byte>(1)), connector.requests.last())
        assertFalse(result.isCompleted)

        // En bekräftelse för en annan characteristic är inte svaret.
        connector.emit(GattEvent.CharacteristicWritten(other, GATT_SUCCESS))
        runCurrent()
        assertFalse(result.isCompleted)

        connector.emit(GattEvent.CharacteristicWritten(lamp, GATT_SUCCESS))
        assertEquals(Outcome.Success(Unit), result.await())
    }

    @Test
    fun `never runs two operations at the same time`() = runTest {
        connected()
        respondManually()
        val read = async { session.read(lamp) }
        val write = async { session.write(other, byteArrayOf(1)) }
        runCurrent()

        assertEquals(FakeRequest.Read(lamp), connector.requests.last())

        connector.emit(GattEvent.CharacteristicRead(lamp, GATT_SUCCESS, byteArrayOf()))
        runCurrent()
        assertEquals(FakeRequest.Write(other, listOf<Byte>(1)), connector.requests.last())

        connector.emit(GattEvent.CharacteristicWritten(other, GATT_SUCCESS))
        read.await()
        assertEquals(Outcome.Success(Unit), write.await())
    }

    @Test
    fun `a cancelled read does not let its late response answer the next read`() = runTest {
        connected()
        respondManually()
        val cancelled = async { session.read(lamp) }
        runCurrent()
        cancelled.cancel()
        val next = async { session.read(lamp) }
        runCurrent()

        connector.emit(GattEvent.CharacteristicRead(lamp, GATT_SUCCESS, byteArrayOf(1)))
        runCurrent()
        connector.emit(GattEvent.CharacteristicRead(lamp, GATT_SUCCESS, byteArrayOf(2)))

        assertArrayEquals(byteArrayOf(2), (next.await() as Outcome.Success).value)
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `an operation that times out closes the connection`() = runTest {
        connected()
        respondManually()
        val result = async { session.read(lamp) }

        advanceTimeBy(GattSession.OPERATION_TIMEOUT + 1.seconds)

        assertEquals(failure(ConnectionError.Timeout), result.await())
        assertEquals(ConnectionState.Failed(ConnectionError.Timeout), session.state.value)
        assertTrue(connector.handle.closed)
    }

    // Avbrott

    @Test
    fun `losing the connection during an operation fails it and closes`() = runTest {
        connected()
        respondManually()
        val result = async { session.read(lamp) }
        runCurrent()

        connector.emit(GattEvent.ConnectionChanged(status = 8, connected = false))

        assertEquals(failure(ConnectionError.ConnectionLost), result.await())
        assertEquals(ConnectionState.Failed(ConnectionError.ConnectionLost), session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `losing the connection while idle is reported in the state`() = runTest {
        connected()

        connector.emit(GattEvent.ConnectionChanged(status = 8, connected = false))

        assertEquals(ConnectionState.Failed(ConnectionError.ConnectionLost), session.state.value)
    }

    @Test
    fun `disconnect ends a pending operation and closes`() = runTest {
        connected()
        respondManually()
        val result = async { session.read(lamp) }
        runCurrent()

        session.disconnect()

        assertEquals(failure(ConnectionError.NotConnected), result.await())
        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `disconnect while connecting stops the connection and keeps the disconnected state`() = runTest {
        connector.connectResponse = null
        val result = async { session.connect() }
        runCurrent()

        session.disconnect()

        assertEquals(failure(ConnectionError.NotConnected), result.await())
        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertTrue(connector.handle.closed)
    }
}
