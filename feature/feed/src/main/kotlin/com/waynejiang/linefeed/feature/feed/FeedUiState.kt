package com.waynejiang.linefeed.feature.feed

import com.waynejiang.linefeed.core.designsystem.UserMessage
import java.time.Instant

/**
 * Everything about the feed screen that is *not* the paged article list (that's a separate
 * `Flow<PagingData<FeedItem>>` — see PLAN.md §5.5 for why it can't live in this `StateFlow`).
 */
data class FeedUiState(
    val weather: WeatherCardState = WeatherCardState.Loading,
    val isOffline: Boolean = false,
    val lastUpdated: Instant? = null,
    val isRefreshingOtherSources: Boolean = false,
    val pendingArticleRefreshId: Long? = null,
    val userMessage: UserMessage? = null,
)
