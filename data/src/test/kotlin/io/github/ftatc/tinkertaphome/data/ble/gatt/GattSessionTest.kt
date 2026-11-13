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
    fun `connects, asks for a larger mtu and discovers services when the device is already bonded`() = runTest {
        assertEquals(Outcome.Success(Unit), session.connect())

        assertEquals(ConnectionState.Connected, session.state.value)
        assertEquals(listOf(FakeRequest.RequestMtu(GattSession.MTU), FakeRequest.DiscoverServices), connector.requests)
    }

    @Test
    fun `connect uses autoConnect and pair does not`() = runTest {
        session.connect()
        session.disconnect()
        session.pair()

        assertEquals(listOf(true, false), connector.autoConnects)
    }

    @Test
    fun `pair bonds before asking for mtu and discovering services when the device is not bonded`() = runTest {
        connector.bonded = false

        assertEquals(Outcome.Success(Unit), session.pair())

        assertEquals(
            listOf(FakeRequest.CreateBond, FakeRequest.RequestMtu(GattSession.MTU), FakeRequest.DiscoverServices),
            connector.requests,
        )
    }

    @Test
    fun `connect never bonds and fails right away when the device is not bonded`() = runTest {
        connector.bonded = false

        assertEquals(failure(ConnectionError.PairingMissing), session.connect())

        assertEquals(0, connector.connectCount)
        assertEquals(ConnectionState.Failed(ConnectionError.PairingMissing), session.state.value)
    }

    @Test
    fun `pair is connecting until the device answers`() = runTest {
        connector.connectResponse = null
        val result = async { session.pair() }
        runCurrent()

        assertEquals(ConnectionState.Connecting, session.state.value)

        connector.emit(GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true))
        assertEquals(Outcome.Success(Unit), result.await())
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `connect waits for the device without a time limit`() = runTest {
        connector.connectResponse = null
        val result = async { session.connect() }

        advanceTimeBy(GattSession.CONNECT_TIMEOUT * 10)

        assertEquals(ConnectionState.Waiting, session.state.value)
        assertFalse(result.isCompleted)

        connector.emit(GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true))
        assertEquals(Outcome.Success(Unit), result.await())
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `is connecting once the waited for device answers`() = runTest {
        connector.connectResponse = null
        respondManually()
        val result = async { session.connect() }
        runCurrent()

        connector.emit(GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true))
        runCurrent()

        assertEquals(ConnectionState.Connecting, session.state.value)
        assertEquals(FakeRequest.RequestMtu(GattSession.MTU), connector.requests.last())
        result.cancel()
    }

    @Test
    fun `operations fail right away while waiting for the device`() = runTest {
        // Regressionstest: en läsning ska inte köa bakom en anslutning som kan vänta hur länge som helst.
        connector.connectResponse = null
        val waiting = async { session.connect() }
        runCurrent()

        assertEquals(failure(ConnectionError.NotConnected), session.read(lamp))
        waiting.cancel()
    }

    @Test
    fun `disconnect stops waiting for the device`() = runTest {
        connector.connectResponse = null
        val result = async { session.connect() }
        runCurrent()

        session.disconnect()

        assertEquals(failure(ConnectionError.NotConnected), result.await())
        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `reports an unavailable connection in the state but leaves an open one alone`() = runTest {
        assertEquals(failure(ConnectionError.BluetoothOff), session.reportUnavailable(ConnectionError.BluetoothOff))
        assertEquals(ConnectionState.Failed(ConnectionError.BluetoothOff), session.state.value)

        connected()
        session.reportUnavailable(ConnectionError.BluetoothOff)
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
        connector.connectResponse = GattEvent.ConnectionChanged(status = 62, connected = false)

        assertEquals(failure(ConnectionError.ConnectFailed(62)), session.connect())

        assertEquals(ConnectionState.Failed(ConnectionError.ConnectFailed(62)), session.state.value)
        assertTrue(connector.handle.closed)
        assertEquals(1, connector.connectCount)
    }

    @Test
    fun `tries again after a pause when android reports gatt error 133`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = GATT_ERROR, connected = false)
        val result = async { session.pair() }
        runCurrent()

        assertEquals(1, connector.connectCount)
        assertTrue(connector.handles.first().closed)
        assertEquals(ConnectionState.Connecting, session.state.value)

        connector.connectResponse = GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true)
        advanceTimeBy(GattSession.CONNECT_RETRY_DELAY + 1.seconds)

        assertEquals(Outcome.Success(Unit), result.await())
        assertEquals(2, connector.connectCount)
    }

    @Test
    fun `gives up when gatt error 133 keeps coming`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = GATT_ERROR, connected = false)

        assertEquals(failure(ConnectionError.ConnectFailed(GATT_ERROR)), session.connect())

        assertEquals(1 + GattSession.CONNECT_RETRIES, connector.connectCount)
        assertEquals(ConnectionState.Failed(ConnectionError.ConnectFailed(GATT_ERROR)), session.state.value)
        assertTrue(connector.handles.all { it.closed })
    }

    @Test
    fun `does not try again when disconnected during the pause`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = GATT_ERROR, connected = false)
        val result = async { session.connect() }
        runCurrent()

        session.disconnect()
        advanceTimeBy(GattSession.CONNECT_RETRY_DELAY + 1.seconds)

        assertEquals(failure(ConnectionError.NotConnected), result.await())
        assertEquals(1, connector.connectCount)
        assertEquals(ConnectionState.Disconnected, session.state.value)
    }

    @Test
    fun `cancelling a connect during the pause reports disconnected`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = GATT_ERROR, connected = false)
        val result = async { session.connect() }
        runCurrent()

        result.cancel()
        runCurrent()

        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertEquals(1, connector.connectCount)
    }

    @Test
    fun `continues when the mtu request is refused or a lower mtu is given`() = runTest {
        connector.requestResult = { if (it is FakeRequest.RequestMtu) GattRequest.REJECTED else GattRequest.STARTED }
        assertEquals(Outcome.Success(Unit), session.connect())
        session.disconnect()

        connector.requestResult = { GattRequest.STARTED }
        connector.respond = { if (it is FakeRequest.RequestMtu) GattEvent.MtuChanged(mtu = 23, status = 4) else connector.successFor(it) }
        assertEquals(Outcome.Success(Unit), session.connect())
    }

    @Test
    fun `pair times out and closes when the device never answers`() = runTest {
        connector.connectResponse = null
        val result = async { session.pair() }

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

        assertEquals(failure(ConnectionError.BondFailed), session.pair())

        assertTrue(connector.handle.closed)
        assertFalse(FakeRequest.DiscoverServices in connector.requests)
    }

    @Test
    fun `keeps waiting while bonding is in progress`() = runTest {
        connector.bonded = false
        connector.respond = { request ->
            if (request == FakeRequest.CreateBond) GattEvent.BondChanged(BondState.BONDING) else connector.successFor(request)
        }
        val result = async { session.pair() }
        runCurrent()

        assertEquals(ConnectionState.Connecting, session.state.value)

        connector.emit(GattEvent.BondChanged(BondState.BONDED))
        assertEquals(Outcome.Success(Unit), result.await())
    }

    @Test
    fun `fails with the gatt status when service discovery fails`() = runTest {
        connector.respond = { request ->
            if (request == FakeRequest.DiscoverServices) GattEvent.ServicesDiscovered(status = 129) else connector.successFor(request)
        }

        assertEquals(failure(ConnectionError.OperationFailed(129)), session.connect())
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `cancelling a connect closes the connection right away and reports disconnected`() = runTest {
        // Regressionstest: tidigare låg statusen kvar som Connecting tills tiden gick ut och blev sedan Failed(Timeout).
        connector.connectResponse = null
        val result = async { session.connect() }
        runCurrent()

        result.cancel()
        runCurrent()

        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertTrue(connector.handle.closed)
        assertTrue(result.isCancelled)
    }

    @Test
    fun `cancelling a pair while bonding closes the connection right away`() = runTest {
        connector.bonded = false
        respondManually()
        val result = async { session.pair() }
        runCurrent()
        assertEquals(FakeRequest.CreateBond, connector.requests.last())

        result.cancel()
        runCurrent()

        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertTrue(connector.handle.closed)
    }

    @Test
    fun `can connect again after a cancelled connect`() = runTest {
        connector.connectResponse = null
        val cancelled = async { session.connect() }
        runCurrent()
        cancelled.cancel()
        runCurrent()
        connector.connectResponse = GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true)

        assertEquals(Outcome.Success(Unit), session.connect())
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `can connect again after a failure`() = runTest {
        connector.connectResponse = GattEvent.ConnectionChanged(status = 62, connected = false)
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
        connector.respond = { GattEvent.CharacteristicRead(lamp, status = 2, value = byteArrayOf()) }

        assertEquals(failure(ConnectionError.OperationFailed(2)), session.read(lamp))
        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `reports missing pairing when the device requires authentication or encryption`() = runTest {
        connected()

        connector.respond = { GattEvent.CharacteristicRead(lamp, status = GATT_INSUFFICIENT_AUTHENTICATION, value = byteArrayOf()) }
        assertEquals(failure(ConnectionError.PairingMissing), session.read(lamp))

        connector.respond = { GattEvent.CharacteristicWritten(lamp, status = GATT_INSUFFICIENT_ENCRYPTION) }
        assertEquals(failure(ConnectionError.PairingMissing), session.write(lamp, byteArrayOf(1)))
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
