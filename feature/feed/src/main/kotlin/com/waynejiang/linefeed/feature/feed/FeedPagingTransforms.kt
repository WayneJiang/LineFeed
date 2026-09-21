package com.waynejiang.linefeed.feature.feed

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.model.ServiceCard

/**
 * `sortIndex == 0` is always the very first article ever loaded (REFRESH assigns 0..n-1, APPEND
 * only ever continues upward — see `ArticleRemoteMediator`), so it's a stable, page-independent way
 * to pick the one top story without the transform needing to know about page boundaries.
 */
fun PagingData<FeedArticle>.toFeedItems(
    services: List<ServiceCard>,
    slots: ServiceCardSlots = ServiceCardSlots(),
): PagingData<FeedItem> = map { feedArticle ->
    if (feedArticle.sortIndex == 0L) {
        FeedItem.TopStory(feedArticle.article, feedArticle.sortIndex)
    } else {
        FeedItem.ArticleRow(feedArticle.article, feedArticle.sortIndex)
    }
}.insertSeparators { _, after ->
    val afterSortIndex = when (after) {
        is FeedItem.TopStory -> after.sortIndex
        is FeedItem.ArticleRow -> after.sortIndex
        is FeedItem.Service, null -> null
    } ?: return@insertSeparators null

    if (services.isEmpty()) return@insertSeparators null
    val slot = slots.slotBefore(afterSortIndex) ?: return@insertSeparators null
    FeedItem.Service(services[slot % services.size], slot)
}
