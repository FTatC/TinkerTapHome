package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import javax.inject.Inject

/**
 * Sätter en utgång till [value] med det kommando som passar utgångens typ. Värden utanför typens
 * intervall begränsas till intervallet, som noden själv gör.
 *
 * Det nya värdet syns i State när noden har satt det. Gränssnittet ska visa State, inte det skickade
 * värdet. En utgång av okänd typ kan inte styras och ger [CommandError.WrongType], utan att något
 * skickas.
 */
class SetOutputUseCase @Inject constructor(
    private val sessions: NodeSessions,
) {
    suspend operator fun invoke(address: BleAddress, output: Output, value: Int): Outcome<Unit, NodeFailure> {
        val command = commandFor(output, value)
            ?: return Outcome.Failure(NodeFailure.Command(CommandError.WrongType))
        return sessions.send(address, command)
    }

    private fun commandFor(output: Output, value: Int): NodeCommand? = when (output.type) {
        OutputType.Digital -> NodeCommand.SetDigital(output.id, on = value != 0)
        OutputType.Pwm -> NodeCommand.SetPwm(output.id, value.coerceIn(OutputType.PWM_RANGE))
        OutputType.Servo -> NodeCommand.SetServo(output.id, value.coerceIn(OutputType.SERVO_RANGE))
        is OutputType.Unknown -> null
    }
}
