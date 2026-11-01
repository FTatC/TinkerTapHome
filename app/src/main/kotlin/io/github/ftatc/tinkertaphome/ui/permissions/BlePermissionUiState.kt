package io.github.ftatc.tinkertaphome.ui.permissions

/** UI-tillståndet för behörighetsflödet. */
sealed interface BlePermissionUiState {

    data object Granted : BlePermissionUiState

    /**
     * Behörigheterna saknas och kan efterfrågas.
     *
     * @property previouslyDenied `true` om användaren redan har nekat en gång, så att förklaringen kan anpassas.
     */
    data class Required(
        val permissions: List<String>,
        val previouslyDenied: Boolean,
    ) : BlePermissionUiState

    /**
     * Användaren har nekat permanent. Behörigheten kan bara slås på i appens inställningar.
     *
     * @property settingsUnavailable `true` om appens inställningar inte gick att öppna. Då visas
     * en beskrivning av var behörigheten slås på i stället för en knapp.
     */
    data class PermanentlyDenied(val settingsUnavailable: Boolean = false) : BlePermissionUiState
}
