package com.waynejiang.linefeed.core.domain.freshness

import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.time.AppClock
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tiny local clock double (not `core:testing`'s `FakeClock`): `core:testing` depends on
 * `core:domain`, so depending on it back from here would be a project dependency cycle.
 */
private class FixedClock(private val instant: Instant) : AppClock {
    override fun now(): Instant = instant
}

class FreshnessPolicyTest {
    private val clock = FixedClock(Instant.parse("2026-09-21T12:00:00Z"))
    private val policy = FreshnessPolicy(clock)

    @Test
    fun `never succeeded fetches`() {
        val decision = policy.evaluate(ContentSource.ARTICLES, null, NetworkStatus.UNMETERED, RefreshTrigger.COLD_START)
        assertEquals(RefreshDecision.Fetch, decision)
    }

    @Test
    fun `age under metered ttl skips as fresh`() {
        val lastSuccess = clock.now().minus(Duration.ofMinutes(59))
        val decision = policy.evaluate(ContentSource.ARTICLES, lastSuccess, NetworkStatus.METERED, RefreshTrigger.FOREGROUND)
        assertEquals(RefreshDecision.Skip(SkipReason.FRESH), decision)
    }

    @Test
    fun `age exactly at ttl fetches (boundary is inclusive)`() {
        val lastSuccess = clock.now().minus(Duration.ofMinutes(60))
        val decision = policy.evaluate(ContentSource.ARTICLES, lastSuccess, NetworkStatus.METERED, RefreshTrigger.FOREGROUND)
        assertEquals(RefreshDecision.Fetch, decision)
    }

    @Test
    fun `same age of 30 minutes differs by network type for articles`() {
        val lastSuccess = clock.now().minus(Duration.ofMinutes(30))

        val metered = policy.evaluate(ContentSource.ARTICLES, lastSuccess, NetworkStatus.METERED, RefreshTrigger.FOREGROUND)
        val unmetered = policy.evaluate(ContentSource.ARTICLES, lastSuccess, NetworkStatus.UNMETERED, RefreshTrigger.FOREGROUND)

        assertEquals(RefreshDecision.Skip(SkipReason.FRESH), metered)
        assertEquals(RefreshDecision.Fetch, unmetered)
    }

    @Test
    fun `offline always skips even when never succeeded or user pulled`() {
        assertEquals(
            RefreshDecision.Skip(SkipReason.OFFLINE),
            policy.evaluate(ContentSource.ARTICLES, null, NetworkStatus.OFFLINE, RefreshTrigger.COLD_START),
        )
        assertEquals(
            RefreshDecision.Skip(SkipReason.OFFLINE),
            policy.evaluate(ContentSource.ARTICLES, null, NetworkStatus.OFFLINE, RefreshTrigger.USER_PULL),
        )
    }

    @Test
    fun `user pull always fetches even when fresh`() {
        val lastSuccess = clock.now()
        val decision = policy.evaluate(ContentSource.ARTICLES, lastSuccess, NetworkStatus.UNMETERED, RefreshTrigger.USER_PULL)
        assertEquals(RefreshDecision.Fetch, decision)
    }

    @Test
    fun `last success in the future (clock turned back) fetches`() {
        val future = clock.now().plus(Duration.ofHours(1))
        val decision = policy.evaluate(ContentSource.ARTICLES, future, NetworkStatus.UNMETERED, RefreshTrigger.FOREGROUND)
        assertEquals(RefreshDecision.Fetch, decision)
    }

    @Test
    fun `each source has an independent ttl`() {
        // Weather is stale after 15 min unmetered, services are still fresh at 12h unmetered.
        val fifteenMinutesAgo = clock.now().minus(Duration.ofMinutes(20))
        val weatherDecision = policy.evaluate(ContentSource.WEATHER, fifteenMinutesAgo, NetworkStatus.UNMETERED, RefreshTrigger.FOREGROUND)
        val servicesDecision = policy.evaluate(ContentSource.SERVICES, fifteenMinutesAgo, NetworkStatus.UNMETERED, RefreshTrigger.FOREGROUND)

        assertEquals(RefreshDecision.Fetch, weatherDecision)
        assertEquals(RefreshDecision.Skip(SkipReason.FRESH), servicesDecision)
    }

    @Test
    fun `isOutdated true after weather outdated threshold`() {
        val threeHoursAgo = clock.now().minus(Duration.ofHours(3))
        assertTrue(policy.isOutdated(ContentSource.WEATHER, threeHoursAgo))
    }

    @Test
    fun `isOutdated false for services which have no threshold`() {
        assertFalse(policy.isOutdated(ContentSource.SERVICES, null))
        val longAgo = clock.now().minus(Duration.ofDays(30))
        assertFalse(policy.isOutdated(ContentSource.SERVICES, longAgo))
    }

    @Test
    fun `isOutdated true when never succeeded for a source with a threshold`() {
        assertTrue(policy.isOutdated(ContentSource.WEATHER, null))
    }
}
