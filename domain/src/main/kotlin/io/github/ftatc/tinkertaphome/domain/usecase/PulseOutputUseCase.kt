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
 * Slår på en digital utgång en kort stund och sedan av igen, som en tryckknapp (`PULSE`).
 *
 * Svaret kommer när pulsen har startat. Att utgången går av igen syns i State. Puls finns bara för
 * digitala utgångar. Andra typer ger [CommandError.WrongType], utan att något skickas.
 */
class PulseOutputUseCase @Inject constructor(
    private val sessions: NodeSessions,
) {
    suspend operator fun invoke(address: BleAddress, output: Output): Outcome<Unit, NodeFailure> {
        if (output.type != OutputType.Digital) return Outcome.Failure(NodeFailure.Command(CommandError.WrongType))
        return sessions.send(address, NodeCommand.Pulse(output.id))
    }
}
