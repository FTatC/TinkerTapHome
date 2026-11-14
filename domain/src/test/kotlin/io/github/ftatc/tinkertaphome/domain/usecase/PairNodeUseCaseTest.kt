package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairNodeUseCaseTest {

    private val address = testAddress(1)
    private val connection = FakeDeviceConnection()
    private val nodeClient = FakeNodeClient()
    private val pairNode = PairNodeUseCase(connection, nodeClient)

    @Test
    fun `pairs and returns the info of the node`() = runTest {
        assertEquals(Outcome.Success(testNodeInfo()), pairNode(address))

        assertEquals(listOf(address), connection.pairCalls)
        assertEquals(listOf(address), nodeClient.infoReads)
        assertTrue(connection.disconnectCalls.isEmpty())
    }

    @Test
    fun `reports a failed pairing without reading info and disconnects`() = runTest {
        connection.connectError = ConnectionError.BondFailed

        assertEquals(Outcome.Failure(NodeFailure.Connection(ConnectionError.BondFailed)), pairNode(address))

        assertTrue(nodeClient.infoReads.isEmpty())
        assertEquals(listOf(address), connection.disconnectCalls)
    }

    @Test
    fun `reports a node with another protocol version and disconnects`() = runTest {
        nodeClient.info = Outcome.Failure(NodeFailure.Node(NodeError.NodeTooOld(protocolVersion = 2)))

        assertEquals(Outcome.Failure(NodeFailure.Node(NodeError.NodeTooOld(protocolVersion = 2))), pairNode(address))
        assertEquals(listOf(address), connection.disconnectCalls)
    }

    @Test
    fun `abort disconnects`() {
        pairNode.abort(address)

        assertEquals(listOf(address), connection.disconnectCalls)
    }
}
