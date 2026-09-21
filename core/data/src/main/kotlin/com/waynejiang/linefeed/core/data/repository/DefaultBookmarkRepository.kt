package com.waynejiang.linefeed.core.data.repository

import android.content.Context
import com.waynejiang.linefeed.core.data.database.dao.BookmarkDao
import com.waynejiang.linefeed.core.data.di.ApplicationScope
import com.waynejiang.linefeed.core.data.mapper.toBookmarkEntity
import com.waynejiang.linefeed.core.data.mapper.toDomain
import com.waynejiang.linefeed.core.data.network.ImageDownloader
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import com.waynejiang.linefeed.core.domain.time.AppClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Bookmarks are their own table (a full snapshot, not a join into the disposable feed cache — see
 * `BookmarkEntity`'s KDoc), so this repository never touches `feed_articles`.
 *
 * Image downloads run on [scope] (process-lifetime), not the caller's coroutine: `setBookmarked`
 * returning shouldn't block on a network fetch, and the caller (a screen) may navigate away or be
 * cancelled long before the download finishes. [downloadJobs] lets un-bookmarking cancel an
 * in-flight download for the same article instead of racing it — without that, a quick
 * bookmark-then-unbookmark could finish downloading *after* the delete and leave a stray
 * `localImagePath` pointing at a file for an article that isn't bookmarked anymore.
 */
internal class DefaultBookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val clock: AppClock,
    @ApplicationContext private val context: Context,
    private val imageDownloader: ImageDownloader,
    @ApplicationScope private val scope: CoroutineScope,
) : BookmarkRepository {

    private val downloadJobs = ConcurrentHashMap<Long, Job>()

    override fun observeSaved(query: String): Flow<List<SavedArticle>> =
        bookmarkDao.observeAll(query.escapeLikeWildcards()).map { entities -> entities.map { it.toDomain() } }

    override fun observeBookmarkedIds(): Flow<Set<Long>> = bookmarkDao.observeIds().map { it.toSet() }

    override fun observeSavedArticle(id: Long): Flow<SavedArticle?> = bookmarkDao.observeById(id).map { it?.toDomain() }

    override suspend fun setBookmarked(article: Article, bookmarked: Boolean) {
        if (bookmarked) {
            bookmarkDao.upsert(article.toBookmarkEntity(savedAt = clock.now()))
            launchDownload(article.id, article.imageUrl)
        } else {
            downloadJobs.remove(article.id)?.cancel()
            val existing = bookmarkDao.findById(article.id)
            bookmarkDao.delete(article.id)
            existing?.localImagePath?.let { path -> File(path).delete() }
        }
    }

    override suspend fun retryPendingImageDownloads() {
        bookmarkDao.pendingImageDownloads().forEach { entity ->
            downloadImage(entity.articleId, entity.imageUrl)
        }
    }

    private fun launchDownload(articleId: Long, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        downloadJobs[articleId]?.cancel()
        downloadJobs[articleId] = scope.launch {
            try {
                downloadImage(articleId, imageUrl)
            } finally {
                downloadJobs.remove(articleId)
            }
        }
    }

    private suspend fun downloadImage(articleId: Long, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        val destination = imageFileFor(articleId)
        val downloaded = imageDownloader.download(imageUrl, destination)
        // Re-check: the article may have been un-bookmarked while this download was in flight.
        if (downloaded && bookmarkDao.findById(articleId) != null) {
            bookmarkDao.updateLocalImagePath(articleId, destination.absolutePath)
        }
    }

    private fun imageFileFor(articleId: Long): File = File(imagesDir(), "$articleId.jpg")

    private fun imagesDir(): File = File(context.filesDir, "bookmark_images").apply { mkdirs() }

    /** `BookmarkDao.observeAll` expects `%`/`_`/`\` already escaped for its `LIKE ... ESCAPE '\'` clause. */
    private fun String.escapeLikeWildcards(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
