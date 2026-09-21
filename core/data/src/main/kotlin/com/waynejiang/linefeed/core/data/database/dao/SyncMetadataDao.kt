package com.waynejiang.linefeed.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.waynejiang.linefeed.core.data.database.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata")
    fun observeAll(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT * FROM sync_metadata WHERE source = :source")
    suspend fun get(source: String): SyncMetadataEntity?

    @Upsert
    suspend fun upsert(entity: SyncMetadataEntity)

    /** Read-modify-write (not a raw UPDATE): needs to create the row on first success, and clear `lastError`. */
    suspend fun markSuccess(source: String, atMillis: Long) {
        val existing = get(source)
        upsert(
            (existing ?: SyncMetadataEntity(source, null, null, null)).copy(
                lastSuccessAtMillis = atMillis,
                lastAttemptAtMillis = atMillis,
                lastError = null,
            ),
        )
    }

    suspend fun markFailure(source: String, atMillis: Long, error: String?) {
        val existing = get(source)
        upsert(
            (existing ?: SyncMetadataEntity(source, null, null, null)).copy(
                lastAttemptAtMillis = atMillis,
                lastError = error,
            ),
        )
    }
}
