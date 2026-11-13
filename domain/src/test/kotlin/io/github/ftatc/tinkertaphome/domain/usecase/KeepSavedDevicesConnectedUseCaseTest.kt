package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepSavedDevicesConnectedUseCaseTest {

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val rela = testDevice(id = 2, name = "Relä")

    private val repository = FakeDeviceRepository(listOf(lampa, rela))
    private val connection = FakeDeviceConnection()
    private val retry = RetryConnectionUseCase(connection)
    private val useCase = KeepSavedDevicesConnectedUseCase(ObserveDevicesUseCase(repository), connection)

    /** Startar use caset och returnerar jobbet, så att testet kan avbryta det som när appen går till bakgrunden. */
    private fun TestScope.start() = backgroundScope.launch { useCase() }.also { runCurrent() }

    private fun stateOf(address: BleAddress) =
        connection.observeState(address).value

    @Test
    fun `connects every saved device`() = runTest {
        start()

        assertEquals(setOf(lampa.address, rela.address), connection.connectCalls.toSet())
        assertEquals(ConnectionState.Connected, stateOf(lampa.address))
        assertEquals(ConnectionState.Connected, stateOf(rela.address))
    }

    @Test
    fun `connects again after a pause when the connection is lost`() = runTest {
        start()
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.ConnectionLost))
        runCurrent()

        assertEquals(1, connection.connectCalls.count { it == lampa.address })

        advanceTimeBy(KeepSavedDevicesConnectedUseCase.RETRY_DELAY + 1.seconds)

        assertEquals(2, connection.connectCalls.count { it == lampa.address })
        assertEquals(ConnectionState.Connected, stateOf(lampa.address))
    }

    @Test
    fun `tries again after a pause when connecting fails for a passing reason`() = runTest {
        connection.connectError = ConnectionError.Timeout
        start()
        connection.connectError = null

        advanceTimeBy(KeepSavedDevicesConnectedUseCase.RETRY_DELAY + 1.seconds)

        assertEquals(ConnectionState.Connected, stateOf(lampa.address))
    }

    @Test
    fun `stops trying when the error needs the user and starts again when it is reset`() = runTest {
        connection.connectError = ConnectionError.PairingMissing
        start()
        connection.connectError = null

        advanceTimeBy(KeepSavedDevicesConnectedUseCase.RETRY_DELAY * 10)

        assertEquals(1, connection.connectCalls.count { it == lampa.address })
        assertEquals(ConnectionState.Failed(ConnectionError.PairingMissing), stateOf(lampa.address))

        retry(lampa.address)
        runCurrent()

        assertEquals(2, connection.connectCalls.count { it == lampa.address })
        assertEquals(ConnectionState.Connected, stateOf(lampa.address))
    }

    @Test
    fun `stops trying while bluetooth is off`() = runTest {
        connection.connectError = ConnectionError.BluetoothOff
        start()

        advanceTimeBy(KeepSavedDevicesConnectedUseCase.RETRY_DELAY * 10)

        assertEquals(1, connection.connectCalls.count { it == lampa.address })
    }

    @Test
    fun `disconnects every device when stopped`() = runTest {
        val job = start()

        job.cancel()
        runCurrent()

        assertEquals(setOf(lampa.address, rela.address), connection.disconnectCalls.toSet())
        assertEquals(ConnectionState.Disconnected, stateOf(lampa.address))
    }

    @Test
    fun `stops waiting for a device that is out of range when stopped`() = runTest {
        connection.holdConnect = true
        val job = start()
        assertEquals(ConnectionState.Waiting, stateOf(lampa.address))

        job.cancel()
        runCurrent()

        assertEquals(ConnectionState.Disconnected, stateOf(lampa.address))
    }

    @Test
    fun `connects a device that is saved later`() = runTest {
        repository.devices.value = listOf(lampa)
        start()

        repository.devices.value = listOf(lampa, rela)
        runCurrent()

        assertEquals(ConnectionState.Connected, stateOf(rela.address))
    }

    @Test
    fun `disconnects a device that is removed`() = runTest {
        start()

        repository.devices.value = listOf(lampa)
        runCurrent()

        assertEquals(listOf(rela.address), connection.disconnectCalls)
        assertEquals(ConnectionState.Disconnected, stateOf(rela.address))
    }

    @Test
    fun `keeps the connections when the saved devices cannot be read`() = runTest {
        start()

        repository.error.value = DeviceError.StorageUnavailable
        runCurrent()

        assertEquals(emptyList<Any>(), connection.disconnectCalls)
        assertEquals(ConnectionState.Connected, stateOf(lampa.address))
    }
}
