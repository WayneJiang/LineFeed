package com.waynejiang.linefeed.core.data.time

import com.waynejiang.linefeed.core.domain.time.AppClock
import java.time.Instant
import javax.inject.Inject

/** The real, wall-clock [AppClock] used everywhere outside tests. */
internal class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Instant = Instant.now()
}
