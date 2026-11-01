package io.github.ftatc.tinkertaphome.ui.permissions

import io.github.ftatc.tinkertaphome.domain.ble.FakeBlePermissionChecker
import org.junit.Assert.assertEquals
import org.junit.Test

class BlePermissionViewModelTest {

    private val checker = FakeBlePermissionChecker()
    private val required = listOf(FakeBlePermissionChecker.SCAN, FakeBlePermissionChecker.CONNECT)

    @Test
    fun `starts granted when permissions are already granted`() {
        checker.granted = true

        assertEquals(BlePermissionUiState.Granted, BlePermissionViewModel(checker).uiState.value)
    }

    @Test
    fun `starts by asking for the required permissions when they are missing`() {
        assertEquals(
            BlePermissionUiState.Required(required, previouslyDenied = false),
            BlePermissionViewModel(checker).uiState.value,
        )
    }

    @Test
    fun `granted result shows the content`() {
        val viewModel = BlePermissionViewModel(checker)

        viewModel.onPermissionResult(PermissionRequestResult.Granted)

        assertEquals(BlePermissionUiState.Granted, viewModel.uiState.value)
    }

    @Test
    fun `denied result asks again and remembers the denial`() {
        val viewModel = BlePermissionViewModel(checker)

        viewModel.onPermissionResult(PermissionRequestResult.Denied)

        assertEquals(BlePermissionUiState.Required(required, previouslyDenied = true), viewModel.uiState.value)
    }

    @Test
    fun `permanently denied result points to the app settings`() {
        val viewModel = BlePermissionViewModel(checker)

        viewModel.onPermissionResult(PermissionRequestResult.PermanentlyDenied)

        assertEquals(BlePermissionUiState.PermanentlyDenied(), viewModel.uiState.value)
    }

    @Test
    fun `resuming after granting in settings shows the content`() {
        val viewModel = BlePermissionViewModel(checker)
        viewModel.onPermissionResult(PermissionRequestResult.PermanentlyDenied)

        checker.granted = true
        viewModel.onResume()

        assertEquals(BlePermissionUiState.Granted, viewModel.uiState.value)
    }

    @Test
    fun `resuming without a change keeps the permanently denied state`() {
        val viewModel = BlePermissionViewModel(checker)
        viewModel.onPermissionResult(PermissionRequestResult.PermanentlyDenied)

        viewModel.onResume()

        assertEquals(BlePermissionUiState.PermanentlyDenied(), viewModel.uiState.value)
    }

    @Test
    fun `resuming after the permission was revoked asks again`() {
        checker.granted = true
        val viewModel = BlePermissionViewModel(checker)

        checker.granted = false
        viewModel.onResume()

        assertEquals(BlePermissionUiState.Required(required, previouslyDenied = false), viewModel.uiState.value)
    }

    @Test
    fun `settings that cannot be opened switch to manual instructions`() {
        val viewModel = BlePermissionViewModel(checker)
        viewModel.onPermissionResult(PermissionRequestResult.PermanentlyDenied)

        viewModel.onSettingsUnavailable()

        assertEquals(BlePermissionUiState.PermanentlyDenied(settingsUnavailable = true), viewModel.uiState.value)
    }

    @Test
    fun `settings unavailable is ignored unless permanently denied`() {
        val viewModel = BlePermissionViewModel(checker)

        viewModel.onSettingsUnavailable()

        assertEquals(BlePermissionUiState.Required(required, previouslyDenied = false), viewModel.uiState.value)
    }

    @Test
    fun `granting manually after settings were unavailable shows the content`() {
        val viewModel = BlePermissionViewModel(checker)
        viewModel.onPermissionResult(PermissionRequestResult.PermanentlyDenied)
        viewModel.onSettingsUnavailable()

        checker.granted = true
        viewModel.onResume()

        assertEquals(BlePermissionUiState.Granted, viewModel.uiState.value)
    }
}
