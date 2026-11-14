package io.github.ftatc.tinkertaphome.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme

/** Inställningsskärmen. Kopplar ViewModel till den tillståndslösa [SettingsContent]. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        actions = SettingsActions(
            onScanDurationChange = viewModel::onScanDurationChange,
            onThemeChange = viewModel::onThemeChange,
            onOnlyNodesInScanChange = viewModel::onOnlyNodesInScanChange,
        ),
    )
}

/** Händelserna från inställningsskärmen. */
data class SettingsActions(
    val onScanDurationChange: (ScanDuration) -> Unit = {},
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onOnlyNodesInScanChange: (Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(uiState: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanSettings(uiState, actions)
            HorizontalDivider()
            ThemeSettings(uiState, actions)
            if (uiState.saveFailed) {
                Text(
                    text = stringResource(R.string.settings_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ScanSettings(uiState: SettingsUiState, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.settings_scan_section))
    Text(stringResource(R.string.settings_scan_duration), style = MaterialTheme.typography.titleSmall)
    Column(modifier = Modifier.selectableGroup()) {
        ScanDuration.entries.forEach { duration ->
            ChoiceRow(
                label = stringResource(R.string.settings_scan_seconds, duration.duration.inWholeSeconds.toInt()),
                selected = uiState.settings.scanDuration == duration,
                onSelect = { actions.onScanDurationChange(duration) },
            )
        }
    }
    OnlyNodesRow(uiState.settings.onlyNodesInScan, actions.onOnlyNodesInScanChange)
}

@Composable
private fun ThemeSettings(uiState: SettingsUiState, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.settings_appearance_section))
    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
    Column(modifier = Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { theme ->
            ChoiceRow(
                label = themeLabel(theme),
                selected = uiState.settings.theme == theme,
                onSelect = { actions.onThemeChange(theme) },
            )
        }
    }
}

@Composable
private fun themeLabel(theme: ThemeMode): String = stringResource(
    when (theme) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** En rad med en radioknapp, där hela raden går att trycka på. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

/** En rad med en strömbrytare, där hela raden går att trycka på. */
@Composable
private fun OnlyNodesRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_only_nodes))
            Text(stringResource(R.string.settings_only_nodes_summary), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun SettingsContentPreview() {
    TinkerTapTheme {
        SettingsContent(uiState = SettingsUiState(), actions = SettingsActions())
    }
}
