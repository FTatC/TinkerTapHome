package io.github.ftatc.tinkertaphome.ui.settings

import io.github.ftatc.tinkertaphome.domain.model.AppSettings

/**
 * UI-tillståndet för inställningsskärmen.
 *
 * @property saveFailed `true` om den senaste ändringen inte gick att spara.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val saveFailed: Boolean = false,
)
