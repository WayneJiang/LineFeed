package com.waynejiang.linefeed.core.domain.model

import java.time.Instant

/**
 * A single feed article. `isBookmarked` is derived at read time (feed cache joined with the
 * bookmark table), never stored redundantly, so there is exactly one place that can go stale.
 */
data class Article(
    val id: Long,
    val title: String,
    val summary: String,
    val newsSite: String,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Instant,
    val authors: List<String>,
    val isBookmarked: Boolean,
)

/**
 * A bookmarked article's own snapshot (title/summary/... copied at save time, not a live join),
 * so it survives the feed cache being cleared or trimmed. See PLAN.md §4.1 for why bookmarks
 * cannot be a boolean flag on the feed cache.
 */
data class SavedArticle(
    val article: Article,
    val savedAt: Instant,
    val localImagePath: String?,
)

/**
 * One element of the paged feed. `sortIndex` is the stable order Paging/Room use (assigned by
 * `ArticleRemoteMediator`: 0..n-1 on REFRESH, continuing from max+1 on APPEND) and is what
 * `ServiceCardSlots` uses to decide where to splice in a service card — it is *not* meant to be
 * shown in the UI.
 */
data class FeedArticle(
    val article: Article,
    val sortIndex: Long,
)
