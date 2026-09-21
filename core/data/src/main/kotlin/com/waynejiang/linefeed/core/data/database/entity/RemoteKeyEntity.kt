package com.waynejiang.linefeed.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per paged feed (today, only `"articles"`): the keyset cursor for APPEND and whether the
 * end has been reached. Kept as one row per feed (not one per article) because keyset pagination
 * only ever needs "what's the next cursor", so per-item remote keys would be pure overhead.
 */
@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val feed: String,
    val nextCursorPublishedAtMillis: Long?,
    val endOfPaginationReached: Boolean,
    val updatedAtMillis: Long,
) {
    companion object {
        const val ARTICLES_FEED = "articles"
    }
}
