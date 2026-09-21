package com.waynejiang.linefeed.core.domain.util

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightTest {
    @Test
    fun `concurrent callers for the same key share one execution`() = runTest {
        val executions = AtomicInteger(0)
        val singleFlight = SingleFlight<String, Int>(backgroundScope)
        val gate = CompletableDeferred<Unit>()

        val a = async {
            singleFlight.run("key") {
                executions.incrementAndGet()
                gate.await()
                42
            }
        }
        val b = async { singleFlight.run("key") { executions.incrementAndGet(); 999 } }

        gate.complete(Unit)

        assertEquals(42, a.await())
        assertEquals(42, b.await())
        assertEquals(1, executions.get())
    }

    @Test
    fun `different keys run independently`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)

        val a = async { singleFlight.run("a") { 1 } }
        val b = async { singleFlight.run("b") { 2 } }

        assertEquals(1, a.await())
        assertEquals(2, b.await())
    }

    @Test
    fun `a fresh call after completion re-executes`() = runTest {
        val executions = AtomicInteger(0)
        val singleFlight = SingleFlight<String, Int>(backgroundScope)

        val first = singleFlight.run("key") { executions.incrementAndGet() }
        val second = singleFlight.run("key") { executions.incrementAndGet() }

        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(2, executions.get())
    }

    @Test
    fun `an exception is delivered to every waiter and does not poison later calls`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)

        val a = async { runCatching { singleFlight.run("key") { error("boom") } } }
        val b = async { runCatching { singleFlight.run("key") { error("boom, but never runs") } } }

        assertTrue(a.await().isFailure)
        assertTrue(b.await().isFailure)

        // Retry: the failed key must not be stuck "in flight" forever.
        val retried = singleFlight.run("key") { 7 }
        assertEquals(7, retried)
    }

    @Test
    fun `cancelling one caller does not cancel the shared work for others`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)
        val gate = CompletableDeferred<Unit>()

        val cancelled = async { singleFlight.run("key") { gate.await(); 42 } }
        val survivor = async { singleFlight.run("key") { -1 } }

        // Let both coroutines actually reach their suspension point (registering "cancelled" as
        // the one running the shared block) before cancelling one of them — otherwise, on
        // `StandardTestDispatcher`, neither coroutine body has started yet and cancelling would
        // just prevent "cancelled" from ever registering, making "survivor" run its own block.
        testScheduler.advanceUntilIdle()

        cancelled.cancel()
        gate.complete(Unit)

        assertEquals(42, survivor.await())
    }
}
