package com.waynejiang.linefeed.core.data.refresh

import com.waynejiang.linefeed.core.data.database.dao.SyncMetadataDao
import com.waynejiang.linefeed.core.data.di.ApplicationScope
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.freshness.RefreshDecision
import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.refresh.FeedRefresher
import com.waynejiang.linefeed.core.domain.refresh.RefreshReport
import com.waynejiang.linefeed.core.domain.refresh.RefreshStatus
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.domain.time.AppClock
import com.waynejiang.linefeed.core.domain.util.SingleFlight
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * The single place any source ever gets refreshed from (see [FeedRefresher]'s own KDoc). Weather
 * and service cards go through [refreshers]; articles are deliberately excluded (see PLAN.md §7.2
 * "v2：文章不經 SourceRefresher") because their only writer is `ArticleRemoteMediator` — this class
 * just bumps [RefreshStatus.articleRefreshRequestId] on the triggers where the feed screen can't
 * detect staleness itself (COLD_START goes through the mediator's own `initialize()`, USER_PULL is
 * handled directly by the UI calling `LazyPagingItems.refresh()`).
 */
internal class DefaultFeedRefresher @Inject constructor(
    private val freshnessPolicy: FreshnessPolicy,
    private val networkMonitor: NetworkMonitor,
    private val syncMetadataDao: SyncMetadataDao,
    private val refreshers: Set<@JvmSuppressWildcards SourceRefresher>,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: AppClock,
) : FeedRefresher {

    private val singleFlight = SingleFlight<ContentSource, SourceResult>(scope)
    private val _status = MutableStateFlow(RefreshStatus())
    override val status: StateFlow<RefreshStatus> = _status.asStateFlow()

    private val articleTriggersThatBumpTheRequestCounter =
        setOf(RefreshTrigger.FOREGROUND, RefreshTrigger.NETWORK_RESTORED)

    override suspend fun refresh(trigger: RefreshTrigger): RefreshReport = coroutineScope {
        val network = networkMonitor.status.first()

        if (trigger in articleTriggersThatBumpTheRequestCounter && shouldFetch(ContentSource.ARTICLES, network, trigger)) {
            _status.update { it.copy(articleRefreshRequestId = it.articleRefreshRequestId + 1) }
        }

        val userInitiated = trigger == RefreshTrigger.USER_PULL
        val toFetch = refreshers.filter { shouldFetch(it.source, network, trigger) }
        _status.update { it.copy(inFlight = toFetch.map(SourceRefresher::source).toSet(), userInitiated = userInitiated) }

        val results = refreshers.associate { refresher ->
            refresher.source to async {
                if (refresher in toFetch) {
                    // Shared across concurrent callers for the same source (e.g. a foreground
                    // event and a network-restored event landing at the same time).
                    singleFlight.run(refresher.source) { refresher.refresh() }
                } else {
                    SourceResult.Skipped(skipReasonFor(refresher.source, network, trigger))
                }
            }
        }.mapValues { it.value.await() }

        val now = clock.now()
        _status.update { current ->
            current.copy(
                inFlight = emptySet(),
                lastResults = current.lastResults + results,
                lastSuccessAt = current.lastSuccessAt + results.filterValues { it is SourceResult.Success }.mapValues { now },
            )
        }
        RefreshReport(results)
    }

    private suspend fun shouldFetch(source: ContentSource, network: NetworkStatus, trigger: RefreshTrigger): Boolean =
        decisionFor(source, network, trigger) is RefreshDecision.Fetch

    private suspend fun skipReasonFor(
        source: ContentSource,
        network: NetworkStatus,
        trigger: RefreshTrigger,
    ) = (decisionFor(source, network, trigger) as RefreshDecision.Skip).reason

    private suspend fun decisionFor(
        source: ContentSource,
        network: NetworkStatus,
        trigger: RefreshTrigger,
    ): RefreshDecision {
        val lastSuccessAt = syncMetadataDao.get(source.name)?.lastSuccessAtMillis?.let(Instant::ofEpochMilli)
        return freshnessPolicy.evaluate(source, lastSuccessAt, network, trigger)
    }
}
