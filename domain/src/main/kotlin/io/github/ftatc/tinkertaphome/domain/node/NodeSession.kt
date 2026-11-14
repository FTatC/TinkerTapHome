package io.github.ftatc.tinkertaphome.domain.node

/**
 * Sessionen med en sparad TinkerTapNode: det appen vet om noden efter att ha läst Info och State och
 * skickat `AUTH` (se "Rekommenderat anslutningsflöde" i `docs/ble-protocol.md`).
 *
 * [outputs] finns kvar när sessionen har stängts, så att gränssnittet kan visa senast kända värden.
 * Noden behåller sina värden när anslutningen bryts, men de kan ha ändrats av en annan telefon.
 */
sealed interface NodeSession {

    /** Utgångarna med sina värden, eller `null` om appen inte har läst dem än. */
    val outputs: NodeOutputs?

    /** Ingen session, till exempel för att noden inte är ansluten. */
    data class Closed(override val outputs: NodeOutputs? = null) : NodeSession

    /** Noden är ansluten och appen läser Info och State och skickar `AUTH`. */
    data class Opening(override val outputs: NodeOutputs? = null) : NodeSession

    /** Noden tar emot kommandon. [outputs] följer nodens State. */
    data class Open(override val outputs: NodeOutputs) : NodeSession

    /** Sessionen stoppades av ett fel. Noden är nedkopplad. */
    data class Failed(val error: NodeSessionError, override val outputs: NodeOutputs? = null) : NodeSession
}

/** Varför en session med en nod stoppades. */
sealed interface NodeSessionError {

    /** Noden kräver ett token, men inget är sparat. */
    data object TokenMissing : NodeSessionError

    /** Anslutningen, nodens Info eller `AUTH` misslyckades. */
    data class Node(val failure: NodeFailure) : NodeSessionError
}

/** Nodens utgångar och deras värden från State, i id-ordning. */
data class NodeOutputs(val info: NodeInfo, val values: List<Int>) {
    init {
        require(values.size == info.outputs.size) {
            "State har ${values.size} värden men Info har ${info.outputs.size} utgångar"
        }
    }

    /** Varje utgång med sitt värde. */
    val outputs: List<OutputValue>
        get() = info.outputs.zip(values, ::OutputValue)
}

/** En utgång och dess värde: `0` eller `1` för digital, `0`–`255` för PWM och grader för servo. */
data class OutputValue(val output: Output, val value: Int)
