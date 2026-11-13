package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand

/**
 * Nodens svar på ett kommando, avkodat från Result: `[opcode][deviceId][status]`.
 *
 * [deviceId] är `null` när svaret inte gäller någon utgång (`0xFF` i protokollet).
 */
internal data class NodeResult(val opcode: Int, val deviceId: Int?, val status: Int) {

    /** `true` om det här är svaret på [command]: samma opcode, och samma utgång om svaret anger en. */
    fun answers(command: NodeCommand): Boolean =
        opcode == command.opcode && (deviceId == null || deviceId == command.targetOutput?.value)

    /** Lyckat om statusen är OK, annars nodens fel. En okänd status räknas aldrig som OK. */
    fun toOutcome(): Outcome<Unit, CommandError> =
        statusError(status)?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)

    companion object {
        private const val LENGTH = 3

        /** Avkodar Result, eller returnerar `null` om värdet inte är exakt tre byte. */
        fun decode(bytes: ByteArray): NodeResult? {
            if (bytes.size != LENGTH) return null
            val deviceId = bytes[1].toUnsigned().takeUnless { it == NodeProtocol.NO_DEVICE }
            return NodeResult(opcode = bytes[0].toUnsigned(), deviceId = deviceId, status = bytes[2].toUnsigned())
        }
    }
}

private fun statusError(status: Int): CommandError? = when (status) {
    0x00 -> null
    0x01 -> CommandError.UnknownCommand
    0x02 -> CommandError.InvalidOutput
    0x03 -> CommandError.WrongType
    0x04 -> CommandError.MissingValue
    0x05 -> CommandError.NotAuthorized
    0x06 -> CommandError.AuthFailed
    0x07 -> CommandError.BadLength
    else -> CommandError.Unknown(status)
}

internal fun Byte.toUnsigned(): Int = toInt() and 0xFF
