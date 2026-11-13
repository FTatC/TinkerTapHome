package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo

/**
 * Avkodar State: en byte per utgång i id-ordning, utan huvud.
 *
 * Har State inte exakt ett värde per utgång i [info] ges ett fel, eftersom appen då inte kan veta
 * vilket värde som hör till vilken utgång.
 */
internal fun decodeState(bytes: ByteArray, info: NodeInfo): Outcome<List<Int>, NodeError> =
    if (bytes.size == info.outputs.size) {
        Outcome.Success(bytes.map { it.toUnsigned() })
    } else {
        Outcome.Failure(NodeError.StateMismatch(expectedOutputs = info.outputs.size, actualValues = bytes.size))
    }
