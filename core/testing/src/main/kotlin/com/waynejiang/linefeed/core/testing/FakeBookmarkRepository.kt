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

    /** Direct control over the underlying snapshot(s) for tests that need fields `setBookmarked` doesn't set, e.g. `localImagePath`. */
    fun setSaved(list: List<SavedArticle>) {
        saved.value = list
    }

    override fun observeBookmarkedIds(): Flow<Set<Long>> = saved.map { list -> list.map { it.article.id }.toSet() }

    override fun observeSavedArticle(id: Long): Flow<SavedArticle?> = saved.map { list -> list.firstOrNull { it.article.id == id } }

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
