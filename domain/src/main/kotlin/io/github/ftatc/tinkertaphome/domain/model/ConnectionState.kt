package io.github.ftatc.tinkertaphome.domain.model

/** Anslutningsstatus för en enhet. */
sealed interface ConnectionState {

    /** Ingen anslutning, antingen från början eller för att användaren kopplade ner. */
    data object Disconnected : ConnectionState

    /**
     * Väntar på att enheten ska komma inom räckhåll. Android ansluter själv när den syns, hur länge
     * det än dröjer.
     */
    data object Waiting : ConnectionState

    /** Enheten har svarat, och anslutning, parkoppling och sökning efter tjänster pågår. */
    data object Connecting : ConnectionState

    /** Enheten är ansluten, parkopplad och redo för läsning och skrivning. */
    data object Connected : ConnectionState

    /** Anslutningen misslyckades eller bröts oväntat. */
    data class Failed(val error: ConnectionError) : ConnectionState
}
