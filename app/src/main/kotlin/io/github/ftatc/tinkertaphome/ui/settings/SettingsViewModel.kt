package io.github.ftatc.tinkertaphome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveSettingsUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.UpdateSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel för inställningsskärmen. Varje ändring sparas direkt, utan en spara-knapp. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(observeSettings(), saveFailed) { settings, failed -> SettingsUiState(settings, failed) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

    fun onScanDurationChange(duration: ScanDuration) {
        save { updateSettings.setScanDuration(duration) }
    }

    fun onThemeChange(theme: ThemeMode) {
        save { updateSettings.setTheme(theme) }
    }

    fun onOnlyNodesInScanChange(onlyNodes: Boolean) {
        save { updateSettings.setOnlyNodesInScan(onlyNodes) }
    }

    private fun save(change: suspend () -> Outcome<Unit, SettingsError>) {
        saveFailed.value = false
        viewModelScope.launch { saveFailed.value = change() is Outcome.Failure }
    }

    private companion object {
        /** Håller flödet vid liv över konfigurationsändringar, till exempel rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
