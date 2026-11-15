package io.github.ftatc.tinkertaphome.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [TinkerTapTheme] med det tema som användaren har valt i inställningarna. */
@Composable
fun AppTheme(viewModel: AppThemeViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    TinkerTapTheme(darkTheme = theme.isDark(systemIsDark = isSystemInDarkTheme()), content = content)
}

/** `true` om appen ska vara mörk med [this] som valt tema och systemet i [systemIsDark]. */
fun ThemeMode.isDark(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Det tema som användaren har valt. Är [ThemeMode.SYSTEM] tills inställningarna är lästa, så att
 * appen följer systemet under den korta stunden.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {

    val theme: StateFlow<ThemeMode> =
        observeSettings()
            .map { it.theme }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
