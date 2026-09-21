package com.waynejiang.linefeed.core.domain.time

import java.time.Instant

/**
 * Indirection over "now" so freshness/TTL logic is deterministic in tests (`FakeClock`) instead of
 * depending on `Instant.now()` / real wall-clock time.
 */
interface AppClock {
    fun now(): Instant
}
