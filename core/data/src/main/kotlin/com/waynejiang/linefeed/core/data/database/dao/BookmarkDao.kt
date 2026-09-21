package com.waynejiang.linefeed.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.waynejiang.linefeed.core.data.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    /**
     * `:query` must already have `%`/`_`/`\` escaped by the caller (repository layer); this DAO
     * only supplies the `ESCAPE '\'` clause and the surrounding wildcards.
     */
    @Query(
        """
        SELECT * FROM bookmarks
        WHERE (:query = '' OR title LIKE '%' || :query || '%' ESCAPE '\' OR newsSite LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY savedAtMillis DESC
        """,
    )
    fun observeAll(query: String = ""): Flow<List<BookmarkEntity>>

    @Query("SELECT articleId FROM bookmarks")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT * FROM bookmarks WHERE articleId = :id")
    fun observeById(id: Long): Flow<BookmarkEntity?>

    @Upsert
    suspend fun upsert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleId = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bookmarks WHERE localImagePath IS NULL AND imageUrl IS NOT NULL")
    suspend fun pendingImageDownloads(): List<BookmarkEntity>

    @Query("UPDATE bookmarks SET localImagePath = :path WHERE articleId = :id")
    suspend fun updateLocalImagePath(id: Long, path: String?)
}
