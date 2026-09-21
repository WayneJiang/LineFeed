package com.waynejiang.linefeed.core.data.repository

import com.waynejiang.linefeed.core.data.database.dao.BookmarkDao
import com.waynejiang.linefeed.core.data.mapper.toBookmarkEntity
import com.waynejiang.linefeed.core.data.mapper.toDomain
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import com.waynejiang.linefeed.core.domain.time.AppClock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bookmarks are their own table (a full snapshot, not a join into the disposable feed cache — see
 * `BookmarkEntity`'s KDoc), so this repository never touches `feed_articles`. Image downloading
 * for offline reading is added in a later step ([retryPendingImageDownloads] is a no-op until then).
 */
internal class DefaultBookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val clock: AppClock,
) : BookmarkRepository {

    override fun observeSaved(query: String): Flow<List<SavedArticle>> =
        bookmarkDao.observeAll(query.escapeLikeWildcards()).map { entities -> entities.map { it.toDomain() } }

    override fun observeBookmarkedIds(): Flow<Set<Long>> = bookmarkDao.observeIds().map { it.toSet() }

    override fun observeSavedArticle(id: Long): Flow<SavedArticle?> = bookmarkDao.observeById(id).map { it?.toDomain() }

    override suspend fun setBookmarked(article: Article, bookmarked: Boolean) {
        if (bookmarked) {
            bookmarkDao.upsert(article.toBookmarkEntity(savedAt = clock.now()))
        } else {
            bookmarkDao.delete(article.id)
        }
    }

    override suspend fun retryPendingImageDownloads() {
        // No-op: image persistence for offline reading ships in a later step (PLAN.md §10 step 12).
    }

    /** `BookmarkDao.observeAll` expects `%`/`_`/`\` already escaped for its `LIKE ... ESCAPE '\'` clause. */
    private fun String.escapeLikeWildcards(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
