package com.waynejiang.linefeed.core.testing

import androidx.paging.PagingData
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.repository.ArticleRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ArticleRepository] fake. `feedPagingData()` wraps the current list with
 * `PagingData.from` (no real paging/mediator behavior) so ViewModel-level tests can assert on
 * `asSnapshot()` without needing Room or a fake `RemoteMediator`.
 */
class FakeArticleRepository(initial: List<FeedArticle> = emptyList()) : ArticleRepository {
    private val feed = MutableStateFlow(initial)
    private val lastSuccessAt = MutableStateFlow<Instant?>(null)

    fun setFeed(articles: List<FeedArticle>) {
        feed.value = articles
    }

    fun setLastSuccessAt(instant: Instant?) {
        lastSuccessAt.value = instant
    }

    override fun feedPagingData(): Flow<PagingData<FeedArticle>> = feed.map { PagingData.from(it) }

    override fun observeArticle(id: Long): Flow<Article?> =
        feed.map { list -> list.firstOrNull { it.article.id == id }?.article }

    override fun observeLastSuccessAt(): Flow<Instant?> = lastSuccessAt
}
