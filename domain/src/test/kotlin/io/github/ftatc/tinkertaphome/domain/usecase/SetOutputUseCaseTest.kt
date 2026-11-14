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

class SetOutputUseCaseTest {

    private val address = testAddress(1)
    private val sessions = FakeNodeSessions(FakeDeviceConnection())
    private val useCase = SetOutputUseCase(sessions)

    private val led = Output(OutputId(0), "led", OutputType.Digital)
    private val fan = Output(OutputId(1), "fan", OutputType.Pwm)
    private val servo = Output(OutputId(2), "servo", OutputType.Servo)

    private fun sentCommand() = sessions.sent.single().second

    @Test
    fun `turns a digital output on with SET`() = runTest {
        assertEquals(Outcome.Success(Unit), useCase(address, led, 1))

        assertEquals(NodeCommand.SetDigital(led.id, on = true), sentCommand())
    }

    @Test
    fun `turns a digital output off with SET`() = runTest {
        useCase(address, led, 0)

        assertEquals(NodeCommand.SetDigital(led.id, on = false), sentCommand())
    }

    @Test
    fun `sets a pwm output with PWM`() = runTest {
        useCase(address, fan, 128)

        assertEquals(NodeCommand.SetPwm(fan.id, 128), sentCommand())
    }

    @Test
    fun `sets a servo with SERVO`() = runTest {
        useCase(address, servo, 90)

        assertEquals(NodeCommand.SetServo(servo.id, 90), sentCommand())
    }

    @Test
    fun `limits values to the range of the output type`() = runTest {
        useCase(address, fan, 300)
        useCase(address, servo, -5)

        assertEquals(
            listOf(NodeCommand.SetPwm(fan.id, 255), NodeCommand.SetServo(servo.id, 0)),
            sessions.sent.map { it.second },
        )
    }

    @Test
    fun `sends nothing to an output of unknown type`() = runTest {
        val unknown = Output(OutputId(3), "motor", OutputType.Unknown("M"))

        assertEquals(Outcome.Failure(NodeFailure.Command(CommandError.WrongType)), useCase(address, unknown, 1))
        assertTrue(sessions.sent.isEmpty())
    }

    @Test
    fun `reports the error from the node`() = runTest {
        val failure = NodeFailure.Connection(ConnectionError.NotConnected)
        sessions.sendResult = { Outcome.Failure(failure) }

        assertEquals(Outcome.Failure(failure), useCase(address, led, 1))
    }
}
