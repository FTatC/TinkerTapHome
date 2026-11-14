package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryConnectionUseCaseTest {

    private val address = testAddress(1)
    private val connection = FakeDeviceConnection()
    private val sessions = FakeNodeSessions(connection)
    private val useCase = RetryConnectionUseCase(connection, sessions)

    @Test
    fun `clears the error of the connection`() {
        connection.setState(address, ConnectionState.Failed(ConnectionError.BluetoothOff))

        useCase(address)

        assertEquals(ConnectionState.Disconnected, connection.observeState(address).value)
    }

    @Test
    fun `clears the error of the session but keeps the last known values`() {
        sessions.setSession(address, NodeSession.Failed(NodeSessionError.TokenMissing, sessions.outputs))

        useCase(address)

        assertEquals(NodeSession.Closed(sessions.outputs), sessions.observe(address).value)
    }
}
