package com.waynejiang.linefeed.core.designsystem.format

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure function (PLAN.md §7.3): `now` is always passed in by the caller (a ViewModel reading
 * `AppClock`), never read from the system clock here, so this is trivially unit-testable and never
 * silently drifts from whatever clock the rest of the app's freshness logic uses.
 */
object RelativeTimeFormatter {
    private val MINUTE = Duration.ofMinutes(1)
    private val HOUR = Duration.ofHours(1)
    private val DAY = Duration.ofDays(1)
    private val WEEK = Duration.ofDays(7)

    fun format(instant: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val duration = Duration.between(instant, now)
        // A future timestamp (clock skew, or a `publishedAt` that is technically after `now`) falls
        // through to the absolute-date branch below rather than showing a nonsensical "-3m ago".
        if (!duration.isNegative && duration < WEEK) {
            return when {
                duration < MINUTE -> "Just now"
                duration < HOUR -> "${duration.toMinutes()}m ago"
                duration < DAY -> "${duration.toHours()}h ago"
                else -> "${duration.toDays()}d ago"
            }
        }
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.US)
            .withZone(zone)
        return formatter.format(instant).substringBeforeLast(",").let { monthDay ->
            // FormatStyle.MEDIUM in en-US is "Sep 18, 2026"; PLAN.md's example is bare "Sep 18".
            monthDay
        }
    }
}
