package io.github.ftatc.tinkertaphome.domain.node

/**
 * Ett kommando till en TinkerTapNode. Hur det kodas till byte ligger i `data`-lagret.
 *
 * Värden utanför tillåtna intervall är programmeringsfel och avvisas när kommandot skapas.
 */
sealed interface NodeCommand {

    /** Slår på eller av en digital utgång. */
    data class SetDigital(val output: OutputId, val on: Boolean) : NodeCommand

    /** Växlar en digital utgång mellan av och på. */
    data class Toggle(val output: OutputId) : NodeCommand

    /** Sätter en PWM-utgång till [value] i [OutputType.PWM_RANGE]. */
    data class SetPwm(val output: OutputId, val value: Int) : NodeCommand {
        init {
            require(value in OutputType.PWM_RANGE) { "PWM-värdet måste vara i ${OutputType.PWM_RANGE}: $value" }
        }
    }

    /** Sätter ett servo till [angle] grader i [OutputType.SERVO_RANGE]. */
    data class SetServo(val output: OutputId, val angle: Int) : NodeCommand {
        init {
            require(angle in OutputType.SERVO_RANGE) { "Vinkeln måste vara i ${OutputType.SERVO_RANGE}: $angle" }
        }
    }

    /** Ber noden skicka utgångarnas aktuella värden. Kräver inte AUTH. */
    data object GetState : NodeCommand

    /** Heartbeat, som nollställer nodens timer för säkert läge. */
    data object Ping : NodeCommand

    /** Låser upp kommandona för den här anslutningen. Fel token gör att noden kopplar ner. */
    data class Authenticate(val token: AuthToken) : NodeCommand

    /** Sätter alla utgångar till sina säkra värden. */
    data object AllOff : NodeCommand
}
