package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AllOffUseCaseTest {

    private val address = testAddress(1)
    private val sessions = FakeNodeSessions(FakeDeviceConnection())
    private val useCase = AllOffUseCase(sessions)

    @Test
    fun `sends ALL_OFF to the node`() = runTest {
        assertEquals(Outcome.Success(Unit), useCase(address))

        assertEquals(listOf(address to NodeCommand.AllOff), sessions.sent)
    }

    @Test
    fun `reports the error from the node`() = runTest {
        val failure = NodeFailure.Command(CommandError.NotAuthorized)
        sessions.sendResult = { Outcome.Failure(failure) }

        assertEquals(Outcome.Failure(failure), useCase(address))
    }
}
