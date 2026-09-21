package com.waynejiang.linefeed.core.testing

import androidx.paging.PagingData
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.repository.ArticleRepository
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

    fun setFeed(articles: List<FeedArticle>) {
        feed.value = articles
    }

    override fun feedPagingData(): Flow<PagingData<FeedArticle>> = feed.map { PagingData.from(it) }

    override fun observeArticle(id: Long): Flow<Article?> =
        feed.map { list -> list.firstOrNull { it.article.id == id }?.article }
}
