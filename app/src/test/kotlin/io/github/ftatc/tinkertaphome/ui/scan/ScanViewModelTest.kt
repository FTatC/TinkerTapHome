package io.github.ftatc.tinkertaphome.ui.scan

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.model.testScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceScanner
import io.github.ftatc.tinkertaphome.domain.usecase.SaveDeviceUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ScanForDevicesUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scanner = FakeDeviceScanner()
    private val repository = FakeDeviceRepository()
    private val viewModel = ScanViewModel(ScanForDevicesUseCase(scanner, repository), SaveDeviceUseCase(repository))

    @Test
    fun `does not scan until started`() {
        assertEquals(ScanUiState(), viewModel.uiState.value)
        assertFalse(scanner.isScanning)
    }

    @Test
    fun `starting shows found devices while scanning`() = runTest(mainDispatcherRule.dispatcher) {
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1))))

        viewModel.startScan()

        assertEquals(ScanUiState(devices = listOf(testScannedDevice(id = 1)), isScanning = true), viewModel.uiState.value)
        assertTrue(scanner.isScanning)
        viewModel.stopScan()
    }

    @Test
    fun `an error stops scanning and is shown`() = runTest(mainDispatcherRule.dispatcher) {
        scanner.emit(Outcome.Failure(BleError.BluetoothOff))

        viewModel.startScan()

        assertEquals(ScanUiState(isScanning = false, error = BleError.BluetoothOff), viewModel.uiState.value)
    }

    @Test
    fun `scanning ends after the scan duration and keeps the found devices`() = runTest(mainDispatcherRule.dispatcher) {
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1))))
        viewModel.startScan()

        advanceTimeBy(ScanForDevicesUseCase.SCAN_DURATION + 1.seconds)

        assertEquals(ScanUiState(devices = listOf(testScannedDevice(id = 1)), isScanning = false), viewModel.uiState.value)
        assertFalse(scanner.isScanning)
    }

    @Test
    fun `stopping stops the scanner and keeps the found devices`() = runTest(mainDispatcherRule.dispatcher) {
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1))))
        viewModel.startScan()

        viewModel.stopScan()

        assertEquals(ScanUiState(devices = listOf(testScannedDevice(id = 1)), isScanning = false), viewModel.uiState.value)
        assertFalse(scanner.isScanning)
    }

    @Test
    fun `starting again clears the previous error and results`() = runTest(mainDispatcherRule.dispatcher) {
        scanner.emit(Outcome.Failure(BleError.BluetoothOff))
        viewModel.startScan()

        scanner.emit(Outcome.Success(emptyList()))
        viewModel.startScan()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertTrue(state.isScanning)
        assertEquals(emptyList<Any>(), state.devices)
        viewModel.stopScan()
    }

    @Test
    fun `clicking a device opens the save dialog with its advertised name`() {
        val device = testScannedDevice(id = 1, name = "Lampa")

        viewModel.onDeviceClick(device)

        assertEquals(SaveDialogState(device = device, name = "Lampa"), viewModel.uiState.value.saveDialog)
    }

    @Test
    fun `a device without a name opens the dialog with an empty name`() {
        viewModel.onDeviceClick(testScannedDevice(id = 1, name = null))

        assertEquals("", viewModel.uiState.value.saveDialog?.name)
    }

    @Test
    fun `dismissing closes the dialog without saving`() {
        viewModel.onDeviceClick(testScannedDevice(id = 1))

        viewModel.onSaveDismiss()

        assertNull(viewModel.uiState.value.saveDialog)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `saving stores the device with the chosen name and closes the dialog`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDeviceClick(testScannedDevice(id = 1, name = "ESP32"))
        viewModel.onNameChange("Lampa i köket")

        viewModel.onSaveConfirm()

        val state = viewModel.uiState.value
        assertNull(state.saveDialog)
        assertEquals(testDevice(id = 1, name = "Lampa i köket"), state.savedDevice)
    }

    @Test
    fun `saving stops the scan`() = runTest(mainDispatcherRule.dispatcher) {
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1))))
        viewModel.startScan()
        viewModel.onDeviceClick(testScannedDevice(id = 1))

        viewModel.onSaveConfirm()

        assertFalse(scanner.isScanning)
        assertFalse(viewModel.uiState.value.isScanning)
    }

    @Test
    fun `a failed save keeps the dialog open and shows the error`() = runTest(mainDispatcherRule.dispatcher) {
        repository.saveError = SaveDeviceError.AlreadySaved
        val device = testScannedDevice(id = 1, name = "Lampa")
        viewModel.onDeviceClick(device)

        viewModel.onSaveConfirm()

        assertEquals(
            SaveDialogState(device = device, name = "Lampa", error = SaveDeviceError.AlreadySaved),
            viewModel.uiState.value.saveDialog,
        )
        assertNull(viewModel.uiState.value.savedDevice)
    }

    @Test
    fun `a blank name is rejected`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDeviceClick(testScannedDevice(id = 1, name = null))

        viewModel.onSaveConfirm()

        assertEquals(SaveDeviceError.NameMissing, viewModel.uiState.value.saveDialog?.error)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `editing the name clears a previous error`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.onDeviceClick(testScannedDevice(id = 1, name = null))
        viewModel.onSaveConfirm()

        viewModel.onNameChange("Lampa")

        assertNull(viewModel.uiState.value.saveDialog?.error)
    }

    @Test
    fun `restarting the scan keeps an open dialog`() = runTest(mainDispatcherRule.dispatcher) {
        // Regressionstest: skanningen startas om varje gång skärmen blir synlig, till exempel efter
        // rotation. Det får inte stänga en dialog där användaren håller på att skriva ett namn.
        viewModel.onDeviceClick(testScannedDevice(id = 1))
        viewModel.onNameChange("Lam")

        viewModel.stopScan()
        viewModel.startScan()

        assertEquals("Lam", viewModel.uiState.value.saveDialog?.name)
        viewModel.stopScan()
    }
}
