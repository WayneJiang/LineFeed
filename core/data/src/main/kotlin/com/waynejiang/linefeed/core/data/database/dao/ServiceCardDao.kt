package com.waynejiang.linefeed.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.waynejiang.linefeed.core.data.database.entity.ServiceCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceCardDao {
    @Query("SELECT * FROM service_cards ORDER BY position ASC")
    fun observeAll(): Flow<List<ServiceCardEntity>>

    @Insert
    suspend fun insertAll(cards: List<ServiceCardEntity>)

    @Query("DELETE FROM service_cards")
    suspend fun clearAll()

    /** Refresh replaces the whole set atomically (small list; simpler and safer than diffing). */
    @Transaction
    suspend fun replaceAll(cards: List<ServiceCardEntity>) {
        clearAll()
        insertAll(cards)
    }
}
