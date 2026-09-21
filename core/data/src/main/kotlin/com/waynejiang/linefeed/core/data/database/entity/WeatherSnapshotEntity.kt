package com.waynejiang.linefeed.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** `dailyJson` is a kotlinx.serialization-encoded `List<DailySnapshotDto>`, always read/written whole. */
@Entity(tableName = "weather_snapshot")
data class WeatherSnapshotEntity(
    @PrimaryKey val locationKey: String,
    val locationName: String,
    val tempC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val observedAtLocal: String,
    val dailyJson: String,
    val fetchedAtMillis: Long,
) {
    companion object {
        const val TAIPEI_LOCATION_KEY = "taipei"
    }
}
