package com.waynejiang.linefeed.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.waynejiang.linefeed.core.data.database.entity.WeatherSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_snapshot WHERE locationKey = :locationKey")
    fun observe(locationKey: String = WeatherSnapshotEntity.TAIPEI_LOCATION_KEY): Flow<WeatherSnapshotEntity?>

    @Query("SELECT * FROM weather_snapshot WHERE locationKey = :locationKey")
    suspend fun get(locationKey: String = WeatherSnapshotEntity.TAIPEI_LOCATION_KEY): WeatherSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: WeatherSnapshotEntity)
}
