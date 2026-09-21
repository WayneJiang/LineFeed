package com.waynejiang.linefeed.feature.feed

import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.ServiceCard

/**
 * One row of the paged feed's `LazyColumn` (PLAN.md §5.5). `key`/`contentType` back
 * `itemKey`/`itemContentType` so Compose can diff/recycle correctly across article rows, the one
 * top-story layout, and the interspersed service cards.
 */
sealed interface FeedItem {
    val key: String
    val contentType: String

    /** The very first article (`sortIndex == 0`), rendered larger than the rest. */
    data class TopStory(val article: Article, val sortIndex: Long) : FeedItem {
        override val key: String = "article-${article.id}"
        override val contentType: String = "top-story"
    }

    data class ArticleRow(val article: Article, val sortIndex: Long) : FeedItem {
        override val key: String = "article-${article.id}"
        override val contentType: String = "article-row"
    }

    /** [slot] (not the card's own id — ids repeat across DummyJSON refreshes) is what makes the key unique. */
    data class Service(val card: ServiceCard, val slot: Int) : FeedItem {
        override val key: String = "service-$slot"
        override val contentType: String = "service"
    }
}
