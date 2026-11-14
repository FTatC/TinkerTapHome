package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairNodeUseCaseTest {

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val connection = FakeDeviceConnection()
    private val sessions = FakeNodeSessions(connection)
    private val useCase = RepairNodeUseCase(connection, sessions)

    private val forgotten = NodeSessionError.Node(NodeFailure.Connection(ConnectionError.PairingMissing))

    @Test
    fun `pairs the node and clears the error of the session`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(forgotten))

        assertEquals(Outcome.Success(Unit), useCase(lampa.address))

        assertEquals(listOf(lampa.address), connection.pairCalls)
        assertEquals(listOf(lampa.address), sessions.resetCalls)
        assertEquals(ConnectionState.Connected, connection.observeState(lampa.address).value)
    }

    @Test
    fun `asks the user to remove the pairing in Android first`() = runTest {
        connection.pairedInAndroid += lampa.address

        assertEquals(Outcome.Failure(RepairNodeError.StillPairedInAndroid), useCase(lampa.address))

        assertTrue(connection.pairCalls.isEmpty())
        assertTrue(sessions.resetCalls.isEmpty())
    }

    @Test
    fun `keeps the error when pairing fails`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(forgotten))
        connection.connectError = ConnectionError.BondFailed

        assertEquals(Outcome.Failure(RepairNodeError.Connection(ConnectionError.BondFailed)), useCase(lampa.address))

        assertTrue(sessions.resetCalls.isEmpty())
        assertEquals(NodeSession.Failed(forgotten), sessions.observe(lampa.address).value)
    }

    @Test
    fun `the app logs in to the node again after pairing`() = runTest {
        val repository = FakeDeviceRepository(listOf(lampa))
        val keep = KeepSavedDevicesConnectedUseCase(ObserveDevicesUseCase(repository), repository, connection, sessions)
        sessions.runError = forgotten
        backgroundScope.launch { keep() }
        runCurrent()
        sessions.runError = null

        advanceTimeBy(KeepSavedDevicesConnectedUseCase.RETRY_DELAY * 10)
        assertEquals(1, sessions.runCalls.size)

        useCase(lampa.address)
        runCurrent()

        assertEquals(2, sessions.runCalls.size)
        assertTrue(sessions.observe(lampa.address).value is NodeSession.Open)
    }
}
