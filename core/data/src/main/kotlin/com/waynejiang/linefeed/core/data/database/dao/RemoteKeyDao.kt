package com.waynejiang.linefeed.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Query("SELECT * FROM remote_keys WHERE feed = :feed")
    suspend fun get(feed: String): RemoteKeyEntity?

    @Upsert
    suspend fun upsert(key: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE feed = :feed")
    suspend fun clear(feed: String)
}
