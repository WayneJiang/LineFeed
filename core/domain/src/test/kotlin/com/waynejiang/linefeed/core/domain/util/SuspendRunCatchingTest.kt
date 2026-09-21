package com.waynejiang.linefeed.core.domain.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendRunCatchingTest {
    @Test
    fun `ordinary exception becomes a failure`() = runTest {
        val result = suspendRunCatching { throw IllegalStateException("boom") }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `success is preserved`() = runTest {
        val result = suspendRunCatching { 42 }
        assertEquals(42, result.getOrNull())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation exception is rethrown, not wrapped`() = runTest {
        suspendRunCatching { throw CancellationException("cancelled") }
    }
}
