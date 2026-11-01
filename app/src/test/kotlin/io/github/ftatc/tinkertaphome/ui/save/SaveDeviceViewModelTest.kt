package io.github.ftatc.tinkertaphome.ui.save

import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.SaveDeviceUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import io.github.ftatc.tinkertaphome.ui.navigation.SaveDeviceRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SaveDeviceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDeviceRepository()

    private fun viewModel(suggestedName: String? = "ESP32") =
        SaveDeviceViewModel(SaveDeviceRoute(testAddress(1).value, suggestedName), SaveDeviceUseCase(repository))

    @Test
    fun `starts with the address and the advertised name from the route`() {
        assertEquals(SaveDeviceUiState(address = testAddress(1), name = "ESP32"), viewModel().uiState.value)
    }

    @Test
    fun `a device without a name starts with an empty name`() {
        assertEquals("", viewModel(suggestedName = null).uiState.value.name)
    }

    @Test
    fun `saving stores the device with the chosen name`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.onNameChange("Lampa i köket")

        viewModel.onSaveConfirm()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(listOf(testDevice(id = 1, name = "Lampa i köket")), repository.devices.value)
    }

    @Test
    fun `a failed save shows the error and can be retried`() = runTest(mainDispatcherRule.dispatcher) {
        repository.saveError = SaveDeviceError.AlreadySaved
        val viewModel = viewModel()

        viewModel.onSaveConfirm()

        val state = viewModel.uiState.value
        assertEquals(SaveDeviceError.AlreadySaved, state.saveError)
        assertFalse(state.isSaving)
        assertFalse(state.isSaved)
    }

    @Test
    fun `a blank name is rejected without touching storage`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel(suggestedName = null)

        viewModel.onSaveConfirm()

        assertEquals(SaveDeviceError.NameMissing, viewModel.uiState.value.saveError)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `editing the name clears a previous error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel(suggestedName = null)
        viewModel.onSaveConfirm()

        viewModel.onNameChange("Lampa")

        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun `confirming again after saving does not save twice`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.onSaveConfirm()

        viewModel.onSaveConfirm()

        assertEquals(1, repository.saveCalls.size)
    }
}
