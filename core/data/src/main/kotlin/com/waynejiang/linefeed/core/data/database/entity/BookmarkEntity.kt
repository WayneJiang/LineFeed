package com.waynejiang.linefeed.core.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user's saved article: a full, standalone snapshot (not a foreign key into `feed_articles`),
 * because the feed cache can be cleared/trimmed at any time and bookmarks must survive that.
 */
@Entity(tableName = "bookmarks", indices = [Index(value = ["savedAtMillis"])])
data class BookmarkEntity(
    @PrimaryKey val articleId: Long,
    val title: String,
    val summary: String,
    val newsSite: String,
    val url: String,
    val imageUrl: String?,
    val localImagePath: String?,
    val publishedAtMillis: Long,
    val authors: String,
    val savedAtMillis: Long,
)
