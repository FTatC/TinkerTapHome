package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeOutputs
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GattNodeSessionsTest {

    private val address = testAddress(1)
    private val connection = FakeDeviceConnection().apply { setState(address, ConnectionState.Connected) }
    private val client = FakeNodeClient()
    private val sessions = GattNodeSessions(connection, client)

    private val token = AuthToken.DEFAULT
    private val info = testNodeInfo()
    private val outputs = NodeOutputs(info, listOf(0))

    /** Startar sessionen som när noden just har anslutits. */
    private fun TestScope.start(token: AuthToken? = this@GattNodeSessionsTest.token): Deferred<NodeSessionError?> =
        backgroundScope.async { sessions.run(address, token) }.also { runCurrent() }

    private fun session() = sessions.observe(address).value

    private fun sentCommands() = client.sent.map { it.second }

    @Test
    fun `reads info and state, authenticates and opens the session`() = runTest {
        start()

        assertEquals(listOf(address), client.infoReads)
        assertEquals(listOf(NodeCommand.Authenticate(token)), sentCommands())
        assertEquals(NodeSession.Open(outputs), session())
    }

    @Test
    fun `shows the values while it authenticates`() = runTest {
        var duringAuth: NodeSession? = null
        client.sendResult = { duringAuth = session(); Outcome.Success(Unit) }

        start()

        assertEquals(NodeSession.Opening(outputs), duringAuth)
    }

    @Test
    fun `sends no AUTH to a node that does not require it`() = runTest {
        client.info = Outcome.Success(testNodeInfo(authRequired = false))

        start(token = null)

        assertTrue(client.sent.isEmpty())
        assertTrue(session() is NodeSession.Open)
    }

    @Test
    fun `stops when the node requires a token that is missing`() = runTest {
        val result = start(token = null)

        assertEquals(NodeSessionError.TokenMissing, result.await())
        assertEquals(NodeSession.Failed(NodeSessionError.TokenMissing), session())
        assertEquals(listOf(address), connection.disconnectCalls)
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun `stops and disconnects when the token is wrong`() = runTest {
        client.sendResult = { Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed)) }

        val result = start()

        val error = NodeSessionError.Node(NodeFailure.Command(CommandError.AuthFailed))
        assertEquals(error, result.await())
        assertEquals(NodeSession.Failed(error, outputs), session())
        assertEquals(listOf(address), connection.disconnectCalls)
    }

    @Test
    fun `shows a wrong token even when the node has already disconnected`() = runTest {
        client.sendResult = {
            connection.setState(address, ConnectionState.Failed(ConnectionError.ConnectionLost))
            Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed))
        }

        val result = start()

        val error = NodeSessionError.Node(NodeFailure.Command(CommandError.AuthFailed))
        assertEquals(error, result.await())
        assertEquals(NodeSession.Failed(error, outputs), session())
    }

    @Test
    fun `stops when the node runs another protocol`() = runTest {
        val failure = NodeFailure.Node(NodeError.NodeTooOld(2))
        client.info = Outcome.Failure(failure)

        val result = start()

        assertEquals(NodeSessionError.Node(failure), result.await())
        assertEquals(NodeSession.Failed(NodeSessionError.Node(failure)), session())
        assertEquals(listOf(address), connection.disconnectCalls)
    }

    @Test
    fun `follows the values in state`() = runTest {
        start()

        client.emitState(Outcome.Success(listOf(1)))
        runCurrent()

        assertEquals(NodeSession.Open(NodeOutputs(info, listOf(1))), session())
    }

    @Test
    fun `stops when state does not match the outputs`() = runTest {
        val result = start()
        val failure = NodeFailure.Node(NodeError.StateMismatch(expectedOutputs = 1, actualValues = 2))

        client.emitState(Outcome.Failure(failure))

        assertEquals(NodeSessionError.Node(failure), result.await())
        assertEquals(listOf(address), connection.disconnectCalls)
    }

    @Test
    fun `ends with the error of a lost connection and keeps the last values`() = runTest {
        val result = start()

        connection.setState(address, ConnectionState.Failed(ConnectionError.ConnectionLost))
        client.endState(NodeFailure.Connection(ConnectionError.ConnectionLost))

        assertEquals(NodeSessionError.Node(NodeFailure.Connection(ConnectionError.ConnectionLost)), result.await())
        assertEquals(NodeSession.Closed(outputs), session())
        assertTrue(connection.disconnectCalls.isEmpty())
    }

    @Test
    fun `ends without error when the node is disconnected`() = runTest {
        val result = start()

        connection.setState(address, ConnectionState.Disconnected)
        client.endState()

        assertNull(result.await())
        assertEquals(NodeSession.Closed(outputs), session())
    }

    @Test
    fun `closes the session and keeps the last values when it is cancelled`() = runTest {
        val result = start()

        result.cancel()
        runCurrent()

        assertEquals(NodeSession.Closed(outputs), session())
    }

    @Test
    fun `sends PING at a third of the heartbeat`() = runTest {
        client.info = Outcome.Success(testNodeInfo(heartbeatTimeout = 3.seconds))
        start()

        advanceTimeBy(3.seconds + 1.seconds / 2)

        assertEquals(listOf(NodeCommand.Authenticate(token), NodeCommand.Ping, NodeCommand.Ping, NodeCommand.Ping), sentCommands())
    }

    @Test
    fun `sends no PING without a heartbeat`() = runTest {
        start()

        advanceTimeBy(10.minutes)

        assertEquals(listOf(NodeCommand.Authenticate(token)), sentCommands())
    }

    @Test
    fun `stops sending PING when the session ends`() = runTest {
        client.info = Outcome.Success(testNodeInfo(heartbeatTimeout = 3.seconds))
        start()
        connection.setState(address, ConnectionState.Disconnected)
        client.endState()
        runCurrent()

        advanceTimeBy(1.minutes)

        assertEquals(listOf(NodeCommand.Authenticate(token)), sentCommands())
    }

    @Test
    fun `sends commands while the session is open`() = runTest {
        start()

        assertEquals(Outcome.Success(Unit), sessions.send(address, NodeCommand.AllOff))
        assertEquals(NodeCommand.AllOff, sentCommands().last())
    }

    @Test
    fun `refuses commands before the session is open`() = runTest {
        assertEquals(
            Outcome.Failure(NodeFailure.Connection(ConnectionError.NotConnected)),
            sessions.send(address, NodeCommand.AllOff),
        )
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun `reset clears an error but keeps the last values`() = runTest {
        client.sendResult = { Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed)) }
        start().await()

        sessions.reset(address)

        assertEquals(NodeSession.Closed(outputs), session())
    }
}
