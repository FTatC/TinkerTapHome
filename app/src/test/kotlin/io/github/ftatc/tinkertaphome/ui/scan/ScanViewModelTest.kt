package io.github.ftatc.tinkertaphome.ui.scan

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.testScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceScanner
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
    private val viewModel = ScanViewModel(ScanForDevicesUseCase(scanner, FakeDeviceRepository()))

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

        assertEquals(ScanUiState(isScanning = false, scanError = BleError.BluetoothOff), viewModel.uiState.value)
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
        assertNull(state.scanError)
        assertTrue(state.isScanning)
        assertEquals(emptyList<Any>(), state.devices)
        viewModel.stopScan()
    }
}
