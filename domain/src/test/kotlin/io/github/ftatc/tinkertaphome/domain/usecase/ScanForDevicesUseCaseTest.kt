package io.github.ftatc.tinkertaphome.domain.usecase

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.model.testScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceScanner
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanForDevicesUseCaseTest {

    private val scanner = FakeDeviceScanner()
    private val repository = FakeDeviceRepository()
    private val scanForDevices = ScanForDevicesUseCase(scanner, repository)

    @Test
    fun `sorts found devices with the strongest signal first`() = runTest {
        scanner.emit(
            Outcome.Success(
                listOf(
                    testScannedDevice(id = 1, rssi = -80),
                    testScannedDevice(id = 2, rssi = -40),
                    testScannedDevice(id = 3, rssi = -60),
                ),
            ),
        )

        scanForDevices().test {
            assertEquals(listOf(2L, 3L, 1L), awaitDeviceIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `devices with equal signal are ordered by address`() = runTest {
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 2, rssi = -50), testScannedDevice(id = 1, rssi = -50))))

        scanForDevices().test {
            assertEquals(listOf(1L, 2L), awaitDeviceIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides devices that are already saved`() = runTest {
        repository.devices.value = listOf(testDevice(id = 1))
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1), testScannedDevice(id = 2))))

        scanForDevices().test {
            assertEquals(listOf(2L), awaitDeviceIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows a device again when it is no longer saved`() = runTest {
        repository.devices.value = listOf(testDevice(id = 1))
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1))))

        scanForDevices().test {
            assertEquals(emptyList<Long>(), awaitDeviceIds())

            repository.devices.value = emptyList()

            assertEquals(listOf(1L), awaitDeviceIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters nothing when saved devices cannot be read`() = runTest {
        repository.error.value = DeviceError.StorageUnavailable
        scanner.emit(Outcome.Success(listOf(testScannedDevice(id = 1))))

        scanForDevices().test {
            assertEquals(listOf(1L), awaitDeviceIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `passes scan errors through`() = runTest {
        scanner.emit(Outcome.Failure(BleError.BluetoothOff))

        scanForDevices().test {
            assertEquals(Outcome.Failure(BleError.BluetoothOff), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stops scanning and completes after the scan duration`() = runTest {
        scanner.emit(Outcome.Success(emptyList()))

        scanForDevices().test {
            awaitItem()
            assertTrue(scanner.isScanning)

            advanceTimeBy(ScanForDevicesUseCase.SCAN_DURATION - 1.seconds)
            assertTrue(scanner.isScanning)

            advanceTimeBy(2.seconds)
            awaitComplete()
            assertFalse(scanner.isScanning)
        }
    }

    @Test
    fun `stops scanning when the collector stops`() = runTest {
        scanner.emit(Outcome.Success(emptyList()))

        scanForDevices().test {
            awaitItem()
            assertTrue(scanner.isScanning)
            cancel()
        }

        assertFalse(scanner.isScanning)
    }

    private suspend fun ReceiveTurbine<Outcome<List<ScannedDevice>, BleError>>.awaitDeviceIds(): List<Long> {
        val outcome = awaitItem()
        check(outcome is Outcome.Success) { "Förväntade Success men fick $outcome" }
        return outcome.value.map { it.address.value.takeLast(2).toLong(16) }
    }
}
