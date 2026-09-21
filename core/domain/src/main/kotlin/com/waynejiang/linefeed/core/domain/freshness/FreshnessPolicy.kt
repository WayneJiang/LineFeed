package com.waynejiang.linefeed.core.domain.freshness

import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.time.AppClock
import java.time.Duration
import java.time.Instant

/**
 * The single, pure decision point for "should this source be fetched right now". Kept as a plain
 * class over [AppClock] + [TtlConfig] (no I/O, no coroutines) so every rule in PLAN.md §3.4 can be
 * unit tested directly without fakes for anything except the clock.
 */
class FreshnessPolicy(
    private val clock: AppClock,
    private val config: TtlConfig = TtlConfig.Default,
) {
    fun evaluate(
        source: ContentSource,
        lastSuccessAt: Instant?,
        network: NetworkStatus,
        trigger: RefreshTrigger,
    ): RefreshDecision {
        if (network == NetworkStatus.OFFLINE) return RefreshDecision.Skip(SkipReason.OFFLINE)
        if (trigger == RefreshTrigger.USER_PULL) return RefreshDecision.Fetch
        if (lastSuccessAt == null) return RefreshDecision.Fetch

        val now = clock.now()
        if (lastSuccessAt.isAfter(now)) return RefreshDecision.Fetch

        val ttl = ttlFor(source, network)
        val age = Duration.between(lastSuccessAt, now)
        return if (age >= ttl) RefreshDecision.Fetch else RefreshDecision.Skip(SkipReason.FRESH)
    }

    /** Whether the source's cache is old enough that the UI should visually flag it (independent of whether we refetch). */
    fun isOutdated(source: ContentSource, lastSuccessAt: Instant?): Boolean {
        val outdatedAfter = config.bySource.getValue(source).outdatedAfter ?: return false
        if (lastSuccessAt == null) return true
        val age = Duration.between(lastSuccessAt, clock.now())
        return age >= outdatedAfter
    }

    private fun ttlFor(source: ContentSource, network: NetworkStatus): Duration {
        val sourceTtl = config.bySource.getValue(source)
        return if (network == NetworkStatus.UNMETERED) sourceTtl.unmetered else sourceTtl.metered
    }
}
