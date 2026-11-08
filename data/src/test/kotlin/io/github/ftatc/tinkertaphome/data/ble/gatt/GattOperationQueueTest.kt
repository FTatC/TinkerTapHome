package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GattOperationQueueTest {

    private var timeouts = 0
    private val queue = GattOperationQueue(onTimeout = { timeouts++ })

    private val discovered = GattEvent.ServicesDiscovered(GATT_SUCCESS)

    /** Svarar på [discovered] med dess status, och ignorerar allt annat. */
    private fun respondToDiscovery(event: GattEvent): Outcome<Int, ConnectionError>? =
        (event as? GattEvent.ServicesDiscovered)?.let { Outcome.Success(it.status) }

    private fun TestScope.startDiscovery(started: MutableList<String> = mutableListOf(), name: String = "op") = async {
        queue.withLock {
            queue.await(TIMEOUT, start = { started += name; null }, respond = ::respondToDiscovery)
        }
    }

    @Test
    fun `returns the response to the operation`() = runTest {
        val result = startDiscovery()
        runCurrent()

        assertTrue(queue.offer(discovered))

        assertEquals(Outcome.Success(GATT_SUCCESS), result.await())
    }

    @Test
    fun `ignores events that are not the response`() = runTest {
        val result = startDiscovery()
        runCurrent()

        assertFalse(queue.offer(GattEvent.BondChanged(BondState.BONDED)))
        assertFalse(result.isCompleted)

        queue.offer(discovered)
        assertEquals(Outcome.Success(GATT_SUCCESS), result.await())
    }

    @Test
    fun `accepts a response that arrives while the operation is starting`() = runTest {
        val result = queue.withLock {
            queue.await(TIMEOUT, start = { queue.offer(discovered); null }, respond = ::respondToDiscovery)
        }

        assertEquals(Outcome.Success(GATT_SUCCESS), result)
    }

    @Test
    fun `returns the start error without waiting`() = runTest {
        val result = queue.withLock {
            queue.await(TIMEOUT, start = { ConnectionError.RequestRejected }, respond = ::respondToDiscovery)
        }

        assertEquals(Outcome.Failure(ConnectionError.RequestRejected), result)
        assertEquals(0, timeouts)
    }

    @Test
    fun `times out and reports it when no response arrives`() = runTest {
        val result = startDiscovery()

        advanceTimeBy(TIMEOUT + 1.seconds)

        assertEquals(Outcome.Failure(ConnectionError.Timeout), result.await())
        assertEquals(1, timeouts)
    }

    @Test
    fun `failPending ends the waiting operation with the error`() = runTest {
        val result = startDiscovery()
        runCurrent()

        queue.failPending(ConnectionError.ConnectionLost)

        assertEquals(Outcome.Failure(ConnectionError.ConnectionLost), result.await())
    }

    @Test
    fun `never runs two operations at the same time`() = runTest {
        val started = mutableListOf<String>()
        val first = startDiscovery(started, "första")
        val second = startDiscovery(started, "andra")
        runCurrent()

        assertEquals(listOf("första"), started)

        queue.offer(discovered)
        runCurrent()
        assertEquals(listOf("första", "andra"), started)

        queue.offer(discovered)
        first.await()
        second.await()
    }

    @Test
    fun `a cancelled operation keeps the queue until its response arrives`() = runTest {
        val started = mutableListOf<String>()
        val cancelled = startDiscovery(started, "avbruten")
        runCurrent()
        cancelled.cancel()
        val next = startDiscovery(started, "nästa")
        runCurrent()

        assertEquals(listOf("avbruten"), started)

        // Svaret på den avbrutna operationen får inte tas för svaret på nästa.
        queue.offer(discovered)
        runCurrent()
        assertEquals(listOf("avbruten", "nästa"), started)
        assertFalse(next.isCompleted)

        queue.offer(discovered)
        assertEquals(Outcome.Success(GATT_SUCCESS), next.await())
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `a cancelled operation that never gets a response reports a timeout`() = runTest {
        val cancelled = startDiscovery()
        runCurrent()
        cancelled.cancel()

        advanceTimeBy(TIMEOUT + 1.seconds)

        assertEquals(1, timeouts)
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `await outside withLock is a programming error`() {
        val error = assertThrows(IllegalStateException::class.java) {
            runTest { queue.await(TIMEOUT, start = { null }, respond = ::respondToDiscovery) }
        }
        assertEquals("await måste anropas inom withLock", error.message)
    }

    @Test
    fun `an operation that is not waiting does not take events`() = runTest {
        launch { startDiscovery().await() }
        runCurrent()
        queue.offer(discovered)
        runCurrent()

        assertFalse(queue.offer(discovered))
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
