package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBookmarkRepository(initial: List<SavedArticle> = emptyList()) : BookmarkRepository {
    private val saved = MutableStateFlow(initial)
    var retryPendingImageDownloadsCallCount: Int = 0
        private set

    override fun observeSaved(query: String): Flow<List<SavedArticle>> = saved.map { list ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.article.title.contains(query, ignoreCase = true) ||
                    it.article.newsSite.contains(query, ignoreCase = true)
            }
        }
    }

    override fun observeBookmarkedIds(): Flow<Set<Long>> = saved.map { list -> list.map { it.article.id }.toSet() }

    override suspend fun setBookmarked(article: Article, bookmarked: Boolean) {
        saved.value = if (bookmarked) {
            saved.value + SavedArticle(article = article.copy(isBookmarked = true), savedAt = article.publishedAt, localImagePath = null)
        } else {
            saved.value.filterNot { it.article.id == article.id }
        }
    }

    override suspend fun retryPendingImageDownloads() {
        retryPendingImageDownloadsCallCount++
    }
}
