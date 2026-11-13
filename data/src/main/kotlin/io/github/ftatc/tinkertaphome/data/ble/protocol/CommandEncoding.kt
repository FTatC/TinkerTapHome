package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.OutputId

/** Kodar kommandot till byten som skrivs till Command: `[opcode][deviceId][value]` eller `[0x20][token]`. */
internal fun NodeCommand.encode(): ByteArray = when (this) {
    is NodeCommand.SetDigital -> bytes(Opcode.SET, output.value, if (on) 1 else 0)
    is NodeCommand.Toggle -> bytes(Opcode.TOGGLE, output.value)
    is NodeCommand.SetPwm -> bytes(Opcode.PWM, output.value, value)
    is NodeCommand.SetServo -> bytes(Opcode.SERVO, output.value, angle)
    NodeCommand.GetState -> bytes(Opcode.GET_STATE)
    NodeCommand.Ping -> bytes(Opcode.PING)
    is NodeCommand.Authenticate -> bytes(Opcode.AUTH) + token.value.toByteArray(Charsets.US_ASCII)
    NodeCommand.AllOff -> bytes(Opcode.ALL_OFF)
}

/** Kommandots opcode, som också står först i nodens Result. */
internal val NodeCommand.opcode: Int
    get() = when (this) {
        is NodeCommand.SetDigital -> Opcode.SET
        is NodeCommand.Toggle -> Opcode.TOGGLE
        is NodeCommand.SetPwm -> Opcode.PWM
        is NodeCommand.SetServo -> Opcode.SERVO
        NodeCommand.GetState -> Opcode.GET_STATE
        NodeCommand.Ping -> Opcode.PING
        is NodeCommand.Authenticate -> Opcode.AUTH
        NodeCommand.AllOff -> Opcode.ALL_OFF
    }

/** Utgången kommandot gäller, eller `null` för kommandon som inte gäller en utgång. */
internal val NodeCommand.targetOutput: OutputId?
    get() = when (this) {
        is NodeCommand.SetDigital -> output
        is NodeCommand.Toggle -> output
        is NodeCommand.SetPwm -> output
        is NodeCommand.SetServo -> output
        NodeCommand.GetState, NodeCommand.Ping, is NodeCommand.Authenticate, NodeCommand.AllOff -> null
    }

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
