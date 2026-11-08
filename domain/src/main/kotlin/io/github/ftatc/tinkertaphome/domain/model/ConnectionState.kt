package io.github.ftatc.tinkertaphome.domain.model

/** Anslutningsstatus för en enhet. */
sealed interface ConnectionState {

    /** Ingen anslutning, antingen från början eller för att användaren kopplade ner. */
    data object Disconnected : ConnectionState

    /** Anslutning, parkoppling och sökning efter tjänster pågår. */
    data object Connecting : ConnectionState

    /** Enheten är ansluten, parkopplad och redo för läsning och skrivning. */
    data object Connected : ConnectionState

    /** Anslutningen misslyckades eller bröts oväntat. */
    data class Failed(val error: ConnectionError) : ConnectionState
}
