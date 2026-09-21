package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.time.AppClock
import java.time.Duration
import java.time.Instant

class FakeClock(initial: Instant = Instant.parse("2026-09-21T00:00:00Z")) : AppClock {
    private var current: Instant = initial

    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }

    fun set(instant: Instant) {
        current = instant
    }
}
