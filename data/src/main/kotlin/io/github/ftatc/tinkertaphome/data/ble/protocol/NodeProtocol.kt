package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import java.util.UUID

/**
 * Konstanter i TinkerTapNodes BLE-protokoll, version [VERSION]. Se `docs/ble-protocol.md`.
 *
 * Får inte ändras på appsidan: riktiga noder förlitar sig på dem (se CLAUDE.md).
 */
internal object NodeProtocol {

    /** Den enda protokollversion appen stöder (fråga 4 i `docs/open-questions.md`). */
    const val VERSION = 3

    val SERVICE: UUID = nodeUuid(0x0000)
    val COMMAND = CharacteristicId(SERVICE, nodeUuid(0x0001))
    val STATE = CharacteristicId(SERVICE, nodeUuid(0x0002))
    val INFO = CharacteristicId(SERVICE, nodeUuid(0x0003))
    val RESULT = CharacteristicId(SERVICE, nodeUuid(0x0004))

    /** `deviceId` i Result när svaret inte gäller någon utgång. */
    const val NO_DEVICE = 0xFF

    private fun nodeUuid(id: Int): UUID = UUID.fromString("7d3c%04x-5b2e-4a8f-9c61-2e4b8a1f7c90".format(id))
}

/** Första byten i ett kommando och i dess Result. */
internal object Opcode {
    const val SET = 0x01
    const val TOGGLE = 0x02
    const val PWM = 0x03
    const val SERVO = 0x04
    const val GET_STATE = 0x10
    const val PING = 0x11
    const val AUTH = 0x20
    const val ALL_OFF = 0xFF
}
