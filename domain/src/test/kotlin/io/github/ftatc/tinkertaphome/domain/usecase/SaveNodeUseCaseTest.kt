package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveNodeUseCaseTest {

    private val address = testAddress(1)
    private val repository = FakeDeviceRepository()
    private val connection = FakeDeviceConnection()
    private val nodeClient = FakeNodeClient()
    private val saveNode = SaveNodeUseCase(SaveDeviceUseCase(repository), connection, nodeClient)

    @Test
    fun `authenticates and saves the node with its token`() = runTest {
        val result = saveNode(address, " Lampa ", testNodeInfo(authRequired = true), AuthToken.DEFAULT)

        assertEquals(Outcome.Success(testDevice(id = 1, name = "Lampa")), result)
        assertEquals(listOf(address to NodeCommand.Authenticate(AuthToken.DEFAULT)), nodeClient.sent)
        assertEquals(AuthToken.DEFAULT, repository.tokens[address])
        assertTrue(connection.disconnectCalls.isEmpty())
    }

    @Test
    fun `connects again before authenticating, since the node may have dropped the connection`() = runTest {
        saveNode(address, "Lampa", testNodeInfo(authRequired = true), AuthToken.DEFAULT)

        assertEquals(listOf(address), connection.pairCalls)
    }

    @Test
    fun `saves a node that needs no token without authenticating`() = runTest {
        val result = saveNode(address, "Lampa", testNodeInfo(authRequired = false), AuthToken.DEFAULT)

        assertTrue(result is Outcome.Success)
        assertTrue(nodeClient.sent.isEmpty())
        assertTrue(repository.tokens.isEmpty())
    }

    @Test
    fun `checks the name before talking to the node`() = runTest {
        val result = saveNode(address, "  ", testNodeInfo(), AuthToken.DEFAULT)

        assertEquals(Outcome.Failure(SaveNodeError.Save(SaveDeviceError.NameMissing)), result)
        assertTrue(nodeClient.sent.isEmpty())
        assertTrue(connection.pairCalls.isEmpty())
    }

    @Test
    fun `needs a token when the node requires authentication`() = runTest {
        assertEquals(Outcome.Failure(SaveNodeError.TokenMissing), saveNode(address, "Lampa", testNodeInfo(), token = null))
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `does not save and disconnects when the token is wrong`() = runTest {
        nodeClient.sendResult = { Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed)) }

        val result = saveNode(address, "Lampa", testNodeInfo(), AuthToken.DEFAULT)

        assertEquals(Outcome.Failure(SaveNodeError.Node(NodeFailure.Command(CommandError.AuthFailed))), result)
        assertTrue(repository.saveCalls.isEmpty())
        assertEquals(listOf(address), connection.disconnectCalls)
        // Appen försöker aldrig igen med samma token på egen hand.
        assertEquals(1, nodeClient.sent.size)
    }

    @Test
    fun `reports a lost connection without authenticating`() = runTest {
        connection.connectError = ConnectionError.ConnectionLost

        val result = saveNode(address, "Lampa", testNodeInfo(), AuthToken.DEFAULT)

        assertEquals(Outcome.Failure(SaveNodeError.Node(NodeFailure.Connection(ConnectionError.ConnectionLost))), result)
        assertTrue(nodeClient.sent.isEmpty())
    }

    @Test
    fun `reports an already saved node and disconnects`() = runTest {
        repository.saveError = SaveDeviceError.AlreadySaved

        val result = saveNode(address, "Lampa", testNodeInfo(), AuthToken.DEFAULT)

        assertEquals(Outcome.Failure(SaveNodeError.Save(SaveDeviceError.AlreadySaved)), result)
        assertEquals(listOf(address), connection.disconnectCalls)
    }
}
