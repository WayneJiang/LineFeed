package com.waynejiang.linefeed.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.waynejiang.linefeed.core.designsystem.UserMessage
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.model.Weather
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.refresh.FeedRefresher
import com.waynejiang.linefeed.core.domain.refresh.RefreshStatus
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.domain.repository.ArticleRepository
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import com.waynejiang.linefeed.core.domain.repository.ServiceCardRepository
import com.waynejiang.linefeed.core.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val WHILE_SUBSCRIBED_5S = SharingStarted.WhileSubscribed(5_000)

/** Snapshot of the three signals [FeedViewModel.uiState] needs from outside its own state, grouped so `combine` never has more than 5 flows in one call (PLAN.md §11). */
private data class CoreSignals(val weather: Weather?, val network: NetworkStatus, val refreshStatus: RefreshStatus)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val weatherRepository: WeatherRepository,
    private val serviceCardRepository: ServiceCardRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val feedRefresher: FeedRefresher,
    networkMonitor: NetworkMonitor,
    private val freshnessPolicy: FreshnessPolicy,
) : ViewModel() {

    private val messageIds = AtomicLong(0)
    private val handledArticleRefreshId = MutableStateFlow(0L)
    private val userMessage = MutableStateFlow<UserMessage?>(null)

    /**
     * Two `cachedIn` calls, not one (PLAN.md §11 "`cachedIn` 與 `combine`"): the first lets this
     * same [ArticleRepository.feedPagingData] flow be collected by `combine` without the
     * "already collected" crash Paging throws on a hot, uncached `Flow<PagingData<T>>`; the second
     * caches the *transformed* result so a configuration change doesn't re-run
     * [toFeedItems]/`insertSeparators` from scratch.
     */
    val feedItems: Flow<PagingData<FeedItem>> = articleRepository.feedPagingData()
        .cachedIn(viewModelScope)
        .combine(serviceCardRepository.observeServiceCards()) { pagingData, services -> pagingData.toFeedItems(services) }
        .cachedIn(viewModelScope)

    private val coreSignals = combine(
        weatherRepository.observeWeather(),
        networkMonitor.status,
        feedRefresher.status,
    ) { weather, network, refreshStatus -> CoreSignals(weather, network, refreshStatus) }

    val uiState: StateFlow<FeedUiState> = combine(
        coreSignals,
        articleRepository.observeLastSuccessAt(),
        handledArticleRefreshId,
        userMessage,
    ) { core, lastUpdated, handledId, message ->
        FeedUiState(
            weather = weatherCardState(core),
            isOffline = core.network == NetworkStatus.OFFLINE,
            lastUpdated = lastUpdated,
            isRefreshingOtherSources = core.refreshStatus.userInitiated && core.refreshStatus.inFlight.isNotEmpty(),
            pendingArticleRefreshId = core.refreshStatus.articleRefreshRequestId.takeIf { it > handledId },
            userMessage = message,
        )
    }.stateIn(viewModelScope, WHILE_SUBSCRIBED_5S, FeedUiState())

    private fun weatherCardState(core: CoreSignals): WeatherCardState {
        val weather = core.weather
        return when {
            weather != null -> WeatherCardState.Available(
                weather = weather,
                isOutdated = freshnessPolicy.isOutdated(ContentSource.WEATHER, weather.fetchedAt),
            )
            core.refreshStatus.lastResults[ContentSource.WEATHER] is SourceResult.Failed -> WeatherCardState.Unavailable
            else -> WeatherCardState.Loading
        }
    }

    /** Weather/service cards only — the article list is refreshed by the screen calling `LazyPagingItems.refresh()` directly. */
    fun onPullToRefresh() {
        viewModelScope.launch {
            val report = feedRefresher.refresh(RefreshTrigger.USER_PULL)
            if (report.results.values.any { it is SourceResult.Failed }) {
                userMessage.value = UserMessage(id = messageIds.incrementAndGet(), messageRes = R.string.feed_refresh_failed)
            }
        }
    }

    /** Called once the screen has issued `lazyPagingItems.refresh()` for this [id], so the next bump can be detected again. */
    fun onArticleRefreshHandled(id: Long) {
        handledArticleRefreshId.value = id
    }

    fun onToggleBookmark(article: Article) {
        viewModelScope.launch {
            bookmarkRepository.setBookmarked(article, bookmarked = !article.isBookmarked)
        }
    }

    fun onUserMessageShown(id: Long) {
        if (userMessage.value?.id == id) {
            userMessage.value = null
        }
    }
}
