package com.waynejiang.linefeed.core.domain.refresh

import com.waynejiang.linefeed.core.domain.model.AppError
import com.waynejiang.linefeed.core.domain.model.ContentSource
import java.time.Instant

sealed interface SourceResult {
    data object Success : SourceResult
    data class Skipped(val reason: com.waynejiang.linefeed.core.domain.freshness.SkipReason) : SourceResult
    data class Failed(val error: AppError) : SourceResult
}

data class RefreshReport(val results: Map<ContentSource, SourceResult>)

/**
 * Live refresh status exposed to the UI. `userInitiated` is what lets the feed screen show a
 * pull-to-refresh spinner only for [com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger.USER_PULL]
 * and stay silent for background stale-while-revalidate refreshes.
 *
 * Articles are refreshed by Paging's `ArticleRemoteMediator`, not by [DefaultFeedRefresher]
 * (renamed reference kept only in KDoc; see `core:data`), so there is no `SourceResult` for
 * `ARTICLES` here for FOREGROUND/NETWORK_RESTORED triggers. Instead, whenever the coordinator
 * decides (via the same [com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy]) that
 * articles are stale on one of those triggers, it bumps [articleRefreshRequestId]; the feed screen
 * observes that counter and calls `LazyPagingItems.refresh()` itself. `COLD_START` is handled by
 * the mediator's own `initialize()`, and `USER_PULL` is handled directly by the UI, so neither
 * needs to go through this counter.
 */
data class RefreshStatus(
    val inFlight: Set<ContentSource> = emptySet(),
    val userInitiated: Boolean = false,
    val lastResults: Map<ContentSource, SourceResult> = emptyMap(),
    val lastSuccessAt: Map<ContentSource, Instant> = emptyMap(),
    val articleRefreshRequestId: Long = 0,
)
