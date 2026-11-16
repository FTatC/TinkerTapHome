package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseOutputUseCaseTest {

    private val address = testAddress(1)
    private val sessions = FakeNodeSessions(FakeDeviceConnection())
    private val useCase = PulseOutputUseCase(sessions)

    private val button = Output(OutputId(1), "btn", OutputType.Digital)

    @Test
    fun `sends PULSE to a digital output`() = runTest {
        assertEquals(Outcome.Success(Unit), useCase(address, button))

        assertEquals(address to NodeCommand.Pulse(button.id), sessions.sent.single())
    }

    @Test
    fun `sends nothing to an output that is not digital`() = runTest {
        val wrongType = Outcome.Failure(NodeFailure.Command(CommandError.WrongType))

        assertEquals(wrongType, useCase(address, Output(OutputId(2), "fan", OutputType.Pwm)))
        assertEquals(wrongType, useCase(address, Output(OutputId(3), "servo", OutputType.Servo)))
        assertEquals(wrongType, useCase(address, Output(OutputId(4), "motor", OutputType.Unknown("M"))))
        assertTrue(sessions.sent.isEmpty())
    }

    @Test
    fun `reports the error from the node`() = runTest {
        val failure = NodeFailure.Connection(ConnectionError.NotConnected)
        sessions.sendResult = { Outcome.Failure(failure) }

        assertEquals(Outcome.Failure(failure), useCase(address, button))
    }
}
