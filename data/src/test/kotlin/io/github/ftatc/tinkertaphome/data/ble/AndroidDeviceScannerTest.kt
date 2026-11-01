package io.github.ftatc.tinkertaphome.data.ble

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.ble.FakeBlePermissionChecker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceScannerTest {

    private val MAX = AndroidDeviceScanner.MAX_FOUND_DEVICES

    private val source = FakeBleScanSource()
    private val permissions = FakeBlePermissionChecker(granted = true)
    private val scanner = AndroidDeviceScanner(source, permissions)

    @Test
    fun `fails without starting a scan when permissions are missing`() = runTest {
        permissions.granted = false

        scanner.scan().test {
            assertEquals(Outcome.Failure(BleError.PermissionMissing), awaitItem())
            awaitComplete()
        }
        assertFalse(source.wasStarted)
    }

    @Test
    fun `fails without starting a scan when bluetooth is off`() = runTest {
        source.bluetoothEnabled = false

        scanner.scan().test {
            assertEquals(Outcome.Failure(BleError.BluetoothOff), awaitItem())
            awaitComplete()
        }
        assertFalse(source.wasStarted)
    }

    @Test
    fun `starts with an empty list`() = runTest {
        scanner.scan().test {
            assertEquals(Outcome.Success(emptyList<ScannedDevice>()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits the full list each time a new device is found`() = runTest {
        scanner.scan().test {
            awaitItem()

            source.send(result(id = 1, name = "Lampa", rssi = -60))
            assertEquals(listOf(device(1, "Lampa", -60)), awaitDevices())

            source.send(result(id = 2, name = "Relä", rssi = -70))
            assertEquals(listOf(device(1, "Lampa", -60), device(2, "Relä", -70)), awaitDevices())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates a device found again instead of adding a duplicate`() = runTest {
        scanner.scan().test {
            awaitItem()
            source.send(result(id = 1, name = "Lampa", rssi = -60))
            awaitItem()

            source.send(result(id = 1, name = "Lampa", rssi = -45))

            assertEquals(listOf(device(1, "Lampa", -45)), awaitDevices())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `treats addresses differing only in case as the same device`() = runTest {
        scanner.scan().test {
            awaitItem()
            source.send(RawScanEvent.Result(address = "aa:bb:cc:dd:ee:01", name = "Lampa", rssi = -60))
            awaitItem()

            source.send(RawScanEvent.Result(address = "AA:BB:CC:DD:EE:01", name = "Lampa", rssi = -50))

            assertEquals(listOf(device(1, "Lampa", -50)), awaitDevices())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps a known name when a later packet has no name`() = runTest {
        scanner.scan().test {
            awaitItem()
            source.send(result(id = 1, name = "Lampa", rssi = -60))
            awaitItem()

            source.send(result(id = 1, name = null, rssi = -50))

            assertEquals(listOf(device(1, "Lampa", -50)), awaitDevices())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores results with an invalid address`() = runTest {
        scanner.scan().test {
            awaitItem()
            source.send(RawScanEvent.Result(address = "trasig", name = "X", rssi = -60))
            source.send(result(id = 1, name = "Lampa", rssi = -60))

            assertEquals(listOf(device(1, "Lampa", -60)), awaitDevices())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scan failure is reported and ends the scan`() = runTest {
        scanner.scan().test {
            awaitItem()

            source.send(RawScanEvent.Failed(errorCode = 2))

            assertEquals(Outcome.Failure(BleError.ScanFailed(2)), awaitItem())
            awaitComplete()
        }
        assertFalse(source.isScanning)
    }

    @Test
    fun `bluetooth turned off during the scan is reported as bluetooth off`() = runTest {
        scanner.scan().test {
            awaitItem()

            source.send(RawScanEvent.BluetoothOff)

            assertEquals(Outcome.Failure(BleError.BluetoothOff), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `permission denied by android is reported as missing permission`() = runTest {
        scanner.scan().test {
            awaitItem()

            source.send(RawScanEvent.PermissionDenied)

            assertEquals(Outcome.Failure(BleError.PermissionMissing), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `stops the scan when the collector stops`() = runTest {
        scanner.scan().test {
            awaitItem()
            assertTrue(source.isScanning)
            cancel()
        }

        assertFalse(source.isScanning)
    }

    @Test
    fun `a new scan starts with an empty list`() = runTest {
        scanner.scan().test {
            awaitItem()
            source.send(result(id = 1, name = "Lampa", rssi = -60))
            awaitItem()
            cancel()
        }

        scanner.scan().test {
            assertEquals(Outcome.Success(emptyList<ScannedDevice>()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps at most the maximum number of devices`() = runTest {
        // Regressionstest: en enhet som skickar reklampaket med slumpade adresser kunde få listan
        // att växa utan gräns under hela skanningen.
        scanner.scan().test {
            awaitItem()

            repeat(MAX + 50) { source.send(numbered(it, rssi = -60)) }

            assertEquals(MAX, expectMostRecentItem().devicesOrFail().size)
        }
    }

    @Test
    fun `when full a stronger new device replaces the weakest`() = runTest {
        scanner.scan().test {
            awaitItem()
            repeat(MAX - 1) { source.send(numbered(it, rssi = -60)) }
            source.send(numbered(MAX - 1, rssi = -90))

            source.send(numbered(MAX, rssi = -40))

            val addresses = expectMostRecentItem().devicesOrFail().map { it.address.value }
            assertEquals(MAX, addresses.size)
            assertTrue(numbered(MAX, rssi = 0).address in addresses)
            assertFalse(numbered(MAX - 1, rssi = 0).address in addresses)
        }
    }

    @Test
    fun `when full a weaker new device is ignored`() = runTest {
        scanner.scan().test {
            awaitItem()
            repeat(MAX) { source.send(numbered(it, rssi = -60)) }
            val full = expectMostRecentItem().devicesOrFail()

            source.send(numbered(MAX, rssi = -90))

            expectNoEvents()
            assertEquals(MAX, full.size)
        }
    }

    @Test
    fun `when full a known device is still updated`() = runTest {
        scanner.scan().test {
            awaitItem()
            repeat(MAX) { source.send(numbered(it, rssi = -60)) }
            expectMostRecentItem()

            source.send(numbered(0, rssi = -30))

            assertEquals(-30, awaitItem().devicesOrFail().first { it.address.value == numbered(0, 0).address }.rssi)
        }
    }

    /** En giltig och unik adress för varje index, även över 255. */
    private fun numbered(index: Int, rssi: Int) = RawScanEvent.Result(
        address = "AA:BB:CC:DD:%02X:%02X".format(index / 256, index % 256),
        name = null,
        rssi = rssi,
    )

    private fun Outcome<List<ScannedDevice>, BleError>.devicesOrFail(): List<ScannedDevice> {
        check(this is Outcome.Success) { "Förväntade Success men fick $this" }
        return value
    }

    private fun result(id: Long, name: String?, rssi: Int) =
        RawScanEvent.Result(address = testAddress(id).value, name = name, rssi = rssi)

    private fun device(id: Long, name: String?, rssi: Int) =
        ScannedDevice(address = testAddress(id), name = name, rssi = rssi)

    private suspend fun ReceiveTurbine<Outcome<List<ScannedDevice>, BleError>>.awaitDevices(): List<ScannedDevice> {
        val outcome = awaitItem()
        check(outcome is Outcome.Success) { "Förväntade Success men fick $outcome" }
        return outcome.value
    }

    /** Fake av Android-skannern. Varje skanning får en egen kanal, som en riktig skanning. */
    private class FakeBleScanSource : BleScanSource {
        var bluetoothEnabled = true
        var wasStarted = false
            private set
        var isScanning = false
            private set

        private var events = Channel<RawScanEvent>(Channel.UNLIMITED)

        override fun isBluetoothEnabled(): Boolean = bluetoothEnabled

        override fun scanEvents(): Flow<RawScanEvent> {
            events = Channel(Channel.UNLIMITED)
            return events.receiveAsFlow()
                .onStart { wasStarted = true; isScanning = true }
                .onCompletion { isScanning = false }
        }

        suspend fun send(event: RawScanEvent) = events.send(event)
    }
}
