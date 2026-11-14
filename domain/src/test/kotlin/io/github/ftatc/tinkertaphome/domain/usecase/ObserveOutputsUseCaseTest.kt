package io.github.ftatc.tinkertaphome.domain.usecase

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveOutputsUseCaseTest {

    private val address = testAddress(1)
    private val sessions = FakeNodeSessions(FakeDeviceConnection())
    private val useCase = ObserveOutputsUseCase(sessions)

    @Test
    fun `follows the session of the node`() = runTest {
        useCase(address).test {
            assertEquals(NodeSession.Closed(), awaitItem())

            sessions.setSession(address, NodeSession.Open(sessions.outputs))

            assertEquals(NodeSession.Open(sessions.outputs), awaitItem())
        }
    }
}
