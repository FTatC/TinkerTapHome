package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.repository.SettingsRepository
import javax.inject.Inject

/** Ändrar en inställning i taget. Varje ändring sparas direkt. */
class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend fun setScanDuration(duration: ScanDuration): Outcome<Unit, SettingsError> =
        repository.update { it.copy(scanDuration = duration) }

    suspend fun setTheme(theme: ThemeMode): Outcome<Unit, SettingsError> =
        repository.update { it.copy(theme = theme) }

    suspend fun setOnlyNodesInScan(onlyNodes: Boolean): Outcome<Unit, SettingsError> =
        repository.update { it.copy(onlyNodesInScan = onlyNodes) }
}
