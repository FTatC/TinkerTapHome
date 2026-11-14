package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeBlePermissionChecker
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AndroidDeviceConnectionTest {

    private val connector = FakeGattConnector()
    private val permissions = FakeBlePermissionChecker(granted = true)
    private val connection = AndroidDeviceConnection(connector, permissions)

    private val lamp = testAddress(1)
    private val relay = testAddress(2)

    @Test
    fun `reports whether Android is paired with the device`() {
        connector.bonded = true
        assertEquals(true, connection.isPaired(lamp))

        connector.bonded = false
        assertEquals(false, connection.isPaired(lamp))
    }

    @Test
    fun `fails without connecting when permissions are missing`() = runTest {
        permissions.granted = false

        assertEquals(Outcome.Failure(ConnectionError.PermissionMissing), connection.connect(lamp))
        assertEquals(0, connector.connectCount)
        assertEquals(ConnectionState.Failed(ConnectionError.PermissionMissing), connection.observeState(lamp).value)
    }

    @Test
    fun `fails without connecting when bluetooth is off`() = runTest {
        connector.bluetoothEnabled = false

        assertEquals(Outcome.Failure(ConnectionError.BluetoothOff), connection.connect(lamp))
        assertEquals(0, connector.connectCount)
        assertEquals(ConnectionState.Failed(ConnectionError.BluetoothOff), connection.observeState(lamp).value)
    }

    @Test
    fun `pair checks bluetooth and connects without autoConnect`() = runTest {
        connector.bluetoothEnabled = false
        assertEquals(Outcome.Failure(ConnectionError.BluetoothOff), connection.pair(lamp))

        connector.bluetoothEnabled = true
        assertEquals(Outcome.Success(Unit), connection.pair(lamp))
        assertEquals(listOf(false), connector.autoConnects)
    }

    @Test
    fun `keeps one state per device`() = runTest {
        connection.connect(lamp)

        assertEquals(ConnectionState.Connected, connection.observeState(lamp).value)
        assertEquals(ConnectionState.Disconnected, connection.observeState(relay).value)
        assertSame(connection.observeState(lamp), connection.observeState(lamp))
    }

    @Test
    fun `reads from the connected device`() = runTest {
        connection.connect(lamp)
        connector.readValue = byteArrayOf(4, 2)

        val result = connection.read(lamp, testCharacteristic())

        assertArrayEquals(byteArrayOf(4, 2), (result as Outcome.Success).value)
    }

    @Test
    fun `writes and returns the reply from the connected device`() = runTest {
        connection.connect(lamp)
        val reply = testCharacteristic(2)
        connector.respond = { request ->
            if (request is FakeRequest.Write) {
                connector.emit(GattEvent.CharacteristicChanged(reply, byteArrayOf(7)))
                GattEvent.CharacteristicWritten(request.characteristic, GATT_SUCCESS)
            } else {
                connector.successFor(request)
            }
        }

        val result = connection.writeAndAwaitReply(lamp, testCharacteristic(1), byteArrayOf(1), reply) { true }

        assertArrayEquals(byteArrayOf(7), (result as Outcome.Success).value)
    }

    @Test
    fun `operations on a device that is not connected fail`() = runTest {
        connection.connect(lamp)

        assertEquals(Outcome.Failure(ConnectionError.NotConnected), connection.read(relay, testCharacteristic()))
    }

    @Test
    fun `disconnect closes the connection and keeps the state readable`() = runTest {
        connection.connect(lamp)

        connection.disconnect(lamp)

        assertEquals(ConnectionState.Disconnected, connection.observeState(lamp).value)
        assertEquals(true, connector.handle.closed)
    }

    @Test
    fun `disconnecting an unknown device does nothing`() {
        connection.disconnect(relay)

        assertEquals(0, connector.connectCount)
    }
}
