package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GattNodeClientTest {

    private val address = testAddress(1)
    private val connection = FakeDeviceConnection()
    private val client = GattNodeClient(connection)

    private val authenticate = NodeCommand.Authenticate(AuthToken.DEFAULT)

    @Test
    fun `reads and parses the info of the node`() = runTest {
        connection.readResults[NodeProtocol.INFO] = Outcome.Success(SPEC_INFO.encodeToByteArray())

        assertEquals(Outcome.Success(testNodeInfo().copy(name = "TinkerTapNode-A1B2")), client.readInfo(address))
    }

    @Test
    fun `reports a node with another protocol`() = runTest {
        connection.readResults[NodeProtocol.INFO] = Outcome.Success(SPEC_INFO.replace("\"proto\":3", "\"proto\":2").encodeToByteArray())

        assertEquals(Outcome.Failure(NodeFailure.Node(NodeError.NodeTooOld(2))), client.readInfo(address))
    }

    @Test
    fun `reports a connection error when info cannot be read`() = runTest {
        connection.readResults[NodeProtocol.INFO] = Outcome.Failure(ConnectionError.ConnectionLost)

        assertEquals(Outcome.Failure(NodeFailure.Connection(ConnectionError.ConnectionLost)), client.readInfo(address))
    }

    @Test
    fun `sends the encoded command and accepts an OK reply`() = runTest {
        connection.replyWith = { Outcome.Success(hex("20 FF 00")) }

        assertEquals(Outcome.Success(Unit), client.send(address, authenticate))
        assertEquals(listOf(NodeProtocol.COMMAND to hex("20 74 69 6E 6B 65 72").toList()), connection.writes)
    }

    @Test
    fun `reports the error the node answers with`() = runTest {
        connection.replyWith = { Outcome.Success(hex("20 FF 06")) }

        assertEquals(Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed)), client.send(address, authenticate))
    }

    @Test
    fun `only accepts the result that answers the command`() = runTest {
        // Ett svar på ett annat kommando är inte svaret, så det blir som om inget svar kom.
        connection.replyWith = { Outcome.Success(hex("01 00 00")) }

        assertEquals(Outcome.Failure(NodeFailure.Connection(ConnectionError.NoReply)), client.send(address, authenticate))
    }

    @Test
    fun `reports a connection error when the command cannot be sent`() = runTest {
        connection.replyWith = { Outcome.Failure(ConnectionError.PairingMissing) }

        assertEquals(Outcome.Failure(NodeFailure.Connection(ConnectionError.PairingMissing)), client.send(address, authenticate))
    }

    private companion object {
        const val SPEC_INFO =
            """{"fw":"0.4.0","proto":3,"name":"TinkerTapNode-A1B2","pair":1,"auth":1,"ato":10000,"hb":0,"dev":[{"id":0,"n":"led","t":"D"}]}"""
    }
}
