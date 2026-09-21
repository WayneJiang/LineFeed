package com.waynejiang.linefeed.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waynejiang.linefeed.core.data.database.dao.BookmarkDao
import com.waynejiang.linefeed.core.data.database.dao.FeedArticleDao
import com.waynejiang.linefeed.core.data.database.dao.RemoteKeyDao
import com.waynejiang.linefeed.core.data.database.dao.ServiceCardDao
import com.waynejiang.linefeed.core.data.database.dao.SyncMetadataDao
import com.waynejiang.linefeed.core.data.database.dao.WeatherDao
import com.waynejiang.linefeed.core.data.database.entity.BookmarkEntity
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleEntity
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity
import com.waynejiang.linefeed.core.data.database.entity.ServiceCardEntity
import com.waynejiang.linefeed.core.data.database.entity.SyncMetadataEntity
import com.waynejiang.linefeed.core.data.database.entity.WeatherSnapshotEntity

/**
 * The one and only local database (PLAN.md §2.4): article cache, bookmarks, weather snapshot,
 * service cards and sync metadata all live here so a bookmark/refresh write and its
 * `sync_metadata` timestamp update can share one transaction.
 *
 * No `fallbackToDestructiveMigration`: bookmarks are user data, so any future schema bump must
 * ship a real migration. Still version 1 (never released), so no migrations exist yet.
 */
@Database(
    entities = [
        FeedArticleEntity::class,
        RemoteKeyEntity::class,
        BookmarkEntity::class,
        WeatherSnapshotEntity::class,
        ServiceCardEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LineFeedDatabase : RoomDatabase() {
    abstract fun feedArticleDao(): FeedArticleDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun weatherDao(): WeatherDao
    abstract fun serviceCardDao(): ServiceCardDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
