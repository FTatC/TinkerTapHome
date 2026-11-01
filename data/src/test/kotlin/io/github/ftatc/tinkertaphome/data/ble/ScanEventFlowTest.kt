package io.github.ftatc.tinkertaphome.data.ble

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEventFlowTest {

    private var stopped = false

    private fun result(index: Int) = RawScanEvent.Result(address = "AA:BB:CC:DD:EE:%02X".format(index % 256), name = null, rssi = -index)

    @Test
    fun `forwards results in order`() = runTest {
        val events = scanEventFlow { listener ->
            listener.onResult(result(1))
            listener.onResult(result(2))
            listener.onFailed(errorCode = 2)
            ScanStart.Started { stopped = true }
        }.toList()

        assertEquals(listOf(result(1), result(2), RawScanEvent.Failed(2)), events)
    }

    @Test
    fun `a scan failure after a burst of results is never dropped`() = runTest {
        // Regressionstest: med standardbufferten (64) misslyckades trySend tyst när bufferten var
        // full. Felet försvann och flödet avslutades som om skanningen hade gått bra.
        val events = scanEventFlow { listener ->
            repeat(500) { listener.onResult(result(it)) }
            listener.onFailed(errorCode = 2)
            ScanStart.Started { stopped = true }
        }.toList()

        assertEquals(RawScanEvent.Failed(2), events.last())
    }

    @Test
    fun `a burst of results keeps the newest ones`() = runTest {
        val events = scanEventFlow { listener ->
            repeat(500) { listener.onResult(result(it)) }
            listener.onFailed(errorCode = 2)
            ScanStart.Started { stopped = true }
        }.toList()

        assertEquals(result(499), events[events.size - 2])
    }

    @Test
    fun `permission denied at start is reported and ends the flow without stopping`() = runTest {
        val events = scanEventFlow { ScanStart.PermissionDenied }.toList()

        assertEquals(listOf(RawScanEvent.PermissionDenied), events)
        assertFalse(stopped)
    }

    @Test
    fun `an unavailable scanner at start is reported and ends the flow`() = runTest {
        assertEquals(listOf(RawScanEvent.Unavailable), scanEventFlow { ScanStart.Unavailable }.toList())
    }

    @Test
    fun `stops the scan when the collector stops`() = runTest {
        scanEventFlow { listener ->
            listener.onResult(result(1))
            ScanStart.Started { stopped = true }
        }.first()

        assertTrue(stopped)
    }

    @Test
    fun `stops the scan after a scan failure`() = runTest {
        scanEventFlow { listener ->
            listener.onFailed(errorCode = 1)
            ScanStart.Started { stopped = true }
        }.toList()

        assertTrue(stopped)
    }
}
