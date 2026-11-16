package io.github.ftatc.tinkertaphome.ui.device

import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.domain.node.OutputValue
import io.github.ftatc.tinkertaphome.domain.usecase.RepairNodeError

/** `true` för fel som en ny parkoppling löser. */
private fun ConnectionError?.needsNewPairing(): Boolean =
    this == ConnectionError.PairingMissing || this is ConnectionError.CharacteristicNotFound

/** UI-tillståndet för detaljskärmen för en sparad enhet. */
sealed interface DeviceDetailUiState {

    data object Loading : DeviceDetailUiState

    /** Enheten finns inte, till exempel för att den har tagits bort. */
    data object NotFound : DeviceDetailUiState

    data class Error(val error: DeviceError) : DeviceDetailUiState

    /**
     * @property session Sessionen med noden: utgångarna och om noden tar emot kommandon.
     * @property isSending `true` medan ett kommando väntar på nodens svar.
     * @property commandError Felet från det senaste kommandot, eller `null`.
     * @property token Det nya token som användaren skriver in efter ett avvisat eller saknat token.
     * @property tokenInvalid `true` om [token] inte är ett giltigt token.
     * @property tokenSaveFailed `true` om det nya tokenet inte gick att spara.
     * @property isRepairing `true` medan appen parkopplar noden igen.
     * @property repairError Felet från det senaste försöket att parkoppla igen, eller `null`.
     * @property pushButtons Namnen på de digitala utgångar som användaren vill se som tryckknappar.
     * @property pushButtonError `true` om valet av tryckknappar inte gick att läsa eller spara.
     */
    data class Content(
        val device: Device,
        val connection: ConnectionState,
        val session: NodeSession = NodeSession.Closed(),
        val isSending: Boolean = false,
        val commandError: NodeFailure? = null,
        val token: String = "",
        val tokenInvalid: Boolean = false,
        val tokenSaveFailed: Boolean = false,
        val isRepairing: Boolean = false,
        val repairError: RepairNodeError? = null,
        val pushButtons: Set<String> = emptySet(),
        val pushButtonError: Boolean = false,
    ) : DeviceDetailUiState {

        /**
         * `true` om [output] visas som tryckknapp, som skickar en puls, i stället för som switch.
         * Bara digitala utgångar kan vara tryckknappar.
         */
        fun isPushButton(output: Output): Boolean = output.type == OutputType.Digital && output.name in pushButtons

        /** Felet som stoppade den senaste anslutningen, om den misslyckades. */
        val error: ConnectionError?
            get() = (connection as? ConnectionState.Failed)?.error

        /** Felet som stoppade sessionen med noden, till exempel fel token. */
        val sessionError: NodeSessionError?
            get() = (session as? NodeSession.Failed)?.error

        /**
         * Användaren kan försöka igen när ett fel har stoppat anslutningen eller sessionen. Saknas
         * parkopplingen hjälper inte det, så då erbjuds att parkoppla igen i stället.
         */
        val canRetry: Boolean
            get() = (connection is ConnectionState.Failed || session is NodeSession.Failed) && !canRepair

        /**
         * Noden kan parkopplas igen: den har glömt telefonen, parkopplingen har tagits bort, eller en
         * characteristic saknas. Det sista beror nästan alltid på att Android har sparat nodens gamla
         * tjänster efter en firmwareuppdatering, och en ny parkoppling hämtar dem på nytt (se
         * `docs/ble-protocol.md`, Fel vid anslutning).
         */
        val canRepair: Boolean
            get() = error.needsNewPairing() ||
                ((sessionError as? NodeSessionError.Node)?.failure as? NodeFailure.Connection)?.error.needsNewPairing()

        /**
         * Formuläret för att parkoppla igen syns medan parkopplingen pågår, även när felet har
         * försvunnit för att appen redan har börjat ansluta.
         */
        val showsRepairForm: Boolean
            get() = canRepair || isRepairing

        /** Utgångarna med sina värden. När noden inte är ansluten är det senast kända värden. */
        val outputs: List<OutputValue>
            get() = session.outputs?.outputs.orEmpty()

        val firmwareVersion: String?
            get() = session.outputs?.info?.firmwareVersion

        /** `true` när värdena inte kommer från en öppen session och kan ha ändrats. */
        val showsLastKnownValues: Boolean
            get() = session !is NodeSession.Open && session.outputs != null

        val isOpening: Boolean
            get() = session is NodeSession.Opening

        /** Utgångarna kan styras när sessionen är öppen och inget annat kommando väntar på svar. */
        val canControl: Boolean
            get() = session is NodeSession.Open && !isSending

        /** Ett fält för nytt token visas när noden har avvisat tokenet eller när inget token är sparat. */
        val asksForToken: Boolean
            get() = when (val error = sessionError) {
                NodeSessionError.TokenMissing -> true
                is NodeSessionError.Node -> error.failure == NodeFailure.Command(CommandError.AuthFailed)
                null -> false
            }
    }
}
