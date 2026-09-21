package com.waynejiang.linefeed.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The paged, disposable article cache. `sortIndex` is the order Paging/Room read back
 * (`ArticleRemoteMediator` assigns it: 0..n-1 on REFRESH, continuing from max+1 on APPEND) and is
 * unique so a mediator bug that writes a duplicate index fails loudly instead of silently
 * reordering the feed. Times are stored as epoch millis (not via a Room TypeConverter) so SQL
 * ordering stays obvious and mapping to `Instant` is an explicit, testable step.
 */
@Entity(
    tableName = "feed_articles",
    indices = [Index(value = ["sortIndex"], unique = true), Index(value = ["publishedAtMillis"])],
)
data class FeedArticleEntity(
    @PrimaryKey val id: Long,
    val sortIndex: Long,
    val title: String,
    val summary: String,
    val newsSite: String,
    val url: String,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    val updatedAtMillis: Long?,
    /** Author names joined with [AUTHORS_SEPARATOR]; see `ArticleMappers`. */
    val authors: String,
    val featured: Boolean,
    val fetchedAtMillis: Long,
) {
    companion object {
        const val AUTHORS_SEPARATOR = "\u001F"
    }
}

/** Result row of `FeedArticleDao.pagingSource()`: a cached article plus whether it is bookmarked. */
data class FeedArticleWithBookmark(
    @Embedded val article: FeedArticleEntity,
    @ColumnInfo(name = "isBookmarked") val isBookmarked: Boolean,
)
