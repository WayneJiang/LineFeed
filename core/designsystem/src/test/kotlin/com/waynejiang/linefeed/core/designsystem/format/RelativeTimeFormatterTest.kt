package com.waynejiang.linefeed.core.designsystem.format

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeFormatterTest {
    private val now = Instant.parse("2026-09-21T12:00:00Z")

    @Test
    fun `under a minute is Just now`() {
        assertEquals("Just now", RelativeTimeFormatter.format(now.minusSeconds(30), now, ZoneOffset.UTC))
    }

    @Test
    fun `minutes ago`() {
        assertEquals("5m ago", RelativeTimeFormatter.format(now.minus(Duration.ofMinutes(5)), now, ZoneOffset.UTC))
    }

    @Test
    fun `hours ago`() {
        assertEquals("2h ago", RelativeTimeFormatter.format(now.minus(Duration.ofHours(2)), now, ZoneOffset.UTC))
    }

    @Test
    fun `days ago, under a week`() {
        assertEquals("3d ago", RelativeTimeFormatter.format(now.minus(Duration.ofDays(3)), now, ZoneOffset.UTC))
    }

    @Test
    fun `exactly a week or more falls back to an absolute date`() {
        assertEquals("Sep 14", RelativeTimeFormatter.format(now.minus(Duration.ofDays(7)), now, ZoneOffset.UTC))
    }

    @Test
    fun `a future instant falls back to an absolute date instead of a negative duration`() {
        assertEquals("Sep 22", RelativeTimeFormatter.format(now.plus(Duration.ofDays(1)), now, ZoneOffset.UTC))
    }

    @Test
    fun `boundary just under a minute`() {
        assertEquals("Just now", RelativeTimeFormatter.format(now.minusSeconds(59), now, ZoneOffset.UTC))
    }

    @Test
    fun `boundary exactly a minute`() {
        assertEquals("1m ago", RelativeTimeFormatter.format(now.minus(Duration.ofMinutes(1)), now, ZoneOffset.UTC))
    }
}
