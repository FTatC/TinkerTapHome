package io.github.ftatc.tinkertaphome.domain.node

import kotlin.time.Duration

/**
 * Det en TinkerTapNode berättar om sig själv i sin Info-characteristic (se `docs/ble-protocol.md`).
 *
 * Info läses vid varje anslutning och sparas inte, eftersom utgångarnas id kan ändras mellan
 * firmwareversioner.
 */
data class NodeInfo(
    /** Firmwareversion, till exempel `0.4.0`. Bara för visning. */
    val firmwareVersion: String,
    val protocolVersion: Int,
    /** Nodens namn, samma som i annonseringen. Rensat från osynliga tecken. */
    val name: String,
    /** `true` om telefonen måste vara parkopplad för att få skicka kommandon. */
    val pairingRequired: Boolean,
    /** `true` om appen måste skicka `AUTH` innan noden tar emot kommandon som ändrar något. */
    val authRequired: Boolean,
    /** Hur länge noden väntar på `AUTH` efter anslutningen. [Duration.ZERO] när AUTH inte krävs. */
    val authTimeout: Duration,
    /** Hur länge noden väntar på ett kommando innan utgångarna går till säkert läge, eller `null` om det är avstängt. */
    val heartbeatTimeout: Duration?,
    /** Nodens utgångar i id-ordning. Har minst en och högst [MAX_OUTPUTS] utgångar. */
    val outputs: List<Output>,
) {
    companion object {
        /** Det största antalet utgångar en nod kan ha. */
        const val MAX_OUTPUTS = 20
    }
}

/** En utgång på noden, till exempel ett relä, en lysdiod eller ett servo. */
data class Output(val id: OutputId, val name: String, val type: OutputType)

/**
 * En utgångs plats i nodens tabell. Id:t kommer från Info och får aldrig hårdkodas, eftersom det kan
 * ändras mellan firmwareversioner.
 */
@JvmInline
value class OutputId(val value: Int) {
    init {
        require(value in 0..MAX_VALUE) { "Ogiltigt utgångs-id: $value" }
    }

    companion object {
        /** Högsta id. Värdet `0xFF` är reserverat för "ingen utgång" i protokollet. */
        const val MAX_VALUE = 0xFE
    }
}

/** Vilken sorts värde en utgång har. */
sealed interface OutputType {

    /** Av eller på. */
    data object Digital : OutputType

    /** Ett värde i [PWM_RANGE], till exempel ljusstyrka eller fläkthastighet. */
    data object Pwm : OutputType

    /** En vinkel i grader i [SERVO_RANGE]. */
    data object Servo : OutputType

    /** En typ som appen inte känner till. Utgången visas men kan inte styras. */
    data class Unknown(val code: String) : OutputType

    companion object {
        val PWM_RANGE: IntRange = 0..255
        val SERVO_RANGE: IntRange = 0..180
    }
}
