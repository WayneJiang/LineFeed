package com.waynejiang.linefeed.core.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleEntity
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleWithBookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedArticleDao {
    @Query(
        """
        SELECT f.*, (b.articleId IS NOT NULL) AS isBookmarked
        FROM feed_articles f
        LEFT JOIN bookmarks b ON b.articleId = f.id
        ORDER BY f.sortIndex ASC
        """,
    )
    fun pagingSource(): PagingSource<Int, FeedArticleWithBookmark>

    /** Backs `ArticleRepository.observeArticle(id)` (used by the detail screen). */
    @Query(
        """
        SELECT f.*, (b.articleId IS NOT NULL) AS isBookmarked
        FROM feed_articles f
        LEFT JOIN bookmarks b ON b.articleId = f.id
        WHERE f.id = :id
        """,
    )
    fun observeById(id: Long): Flow<FeedArticleWithBookmark?>

    @Query("SELECT * FROM feed_articles WHERE id = :id")
    suspend fun findById(id: Long): FeedArticleEntity?

    /** IGNORE (not REPLACE): boundary articles re-fetched on APPEND must keep their existing `sortIndex`. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<FeedArticleEntity>)

    @Query("DELETE FROM feed_articles")
    suspend fun clearAll()

    @Query("SELECT id FROM feed_articles WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<Long>): List<Long>

    @Query("SELECT MAX(sortIndex) FROM feed_articles")
    suspend fun maxSortIndex(): Long?

    @Query("SELECT COUNT(*) FROM feed_articles")
    suspend fun count(): Int
}
