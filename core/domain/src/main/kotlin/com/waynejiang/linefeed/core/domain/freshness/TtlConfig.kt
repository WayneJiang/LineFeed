package com.waynejiang.linefeed.core.domain.freshness

import com.waynejiang.linefeed.core.domain.model.ContentSource
import java.time.Duration

/** TTL pair for one source: shorter on unmetered (cheap, prefer fresher), longer on metered (save data). */
data class SourceTtl(
    val unmetered: Duration,
    val metered: Duration,
    val outdatedAfter: Duration?,
)

/**
 * Central place for every source's freshness window (PLAN.md §3.2). Kept as data (not hardcoded
 * inside [FreshnessPolicy]) so a future "data saver" setting only needs to swap this value.
 */
data class TtlConfig(val bySource: Map<ContentSource, SourceTtl>) {
    companion object {
        val Default = TtlConfig(
            bySource = mapOf(
                ContentSource.WEATHER to SourceTtl(
                    unmetered = Duration.ofMinutes(15),
                    metered = Duration.ofMinutes(30),
                    outdatedAfter = Duration.ofHours(3),
                ),
                ContentSource.ARTICLES to SourceTtl(
                    unmetered = Duration.ofMinutes(20),
                    metered = Duration.ofMinutes(60),
                    outdatedAfter = Duration.ofHours(12),
                ),
                ContentSource.SERVICES to SourceTtl(
                    unmetered = Duration.ofHours(12),
                    metered = Duration.ofHours(24),
                    outdatedAfter = null,
                ),
            ),
        )
    }
}
