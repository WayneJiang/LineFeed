package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.data.database.entity.WeatherSnapshotEntity
import com.waynejiang.linefeed.core.data.network.dto.ForecastResponseDto
import com.waynejiang.linefeed.core.domain.model.CurrentWeather
import com.waynejiang.linefeed.core.domain.model.DailyForecast
import com.waynejiang.linefeed.core.domain.model.Weather
import com.waynejiang.linefeed.core.domain.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Persisted form of one `daily` entry; `dailyJson` on [WeatherSnapshotEntity] is a `List<DailySnapshotDto>`. */
@Serializable
internal data class DailySnapshotDto(
    val date: String,
    val weatherCode: Int,
    val maxC: Double,
    val minC: Double,
)

private val weatherJson = Json { ignoreUnknownKeys = true }

private const val TAIPEI_LOCATION_NAME = "Taipei"

fun ForecastResponseDto.toEntity(fetchedAt: Instant): WeatherSnapshotEntity {
    // Open-Meteo's `daily` fields are parallel arrays; defensively zip to the shortest length
    // instead of trusting they always match (PLAN.md §6.2).
    val size = minOf(daily.time.size, daily.weatherCode.size, daily.temperature2mMax.size, daily.temperature2mMin.size)
    val dailySnapshots = (0 until size).map { i ->
        DailySnapshotDto(
            date = daily.time[i],
            weatherCode = daily.weatherCode[i],
            maxC = daily.temperature2mMax[i],
            minC = daily.temperature2mMin[i],
        )
    }
    return WeatherSnapshotEntity(
        locationKey = WeatherSnapshotEntity.TAIPEI_LOCATION_KEY,
        locationName = TAIPEI_LOCATION_NAME,
        tempC = current.temperature2m,
        weatherCode = current.weatherCode,
        isDay = current.isDay == 1,
        observedAtLocal = current.time,
        dailyJson = weatherJson.encodeToString(dailySnapshots),
        fetchedAtMillis = fetchedAt.toEpochMilli(),
    )
}

/** [today]: dates before it are dropped so an overnight-stale cache doesn't show yesterday's forecast. */
fun WeatherSnapshotEntity.toDomain(today: LocalDate): Weather {
    val dailySnapshots = runCatching { weatherJson.decodeFromString<List<DailySnapshotDto>>(dailyJson) }.getOrDefault(emptyList())
    return Weather(
        locationName = locationName,
        current = CurrentWeather(
            temperatureC = tempC,
            condition = WeatherCondition.fromWmo(weatherCode),
            isDay = isDay,
            observedAt = LocalDateTime.parse(observedAtLocal),
        ),
        daily = dailySnapshots
            .map {
                DailyForecast(
                    date = LocalDate.parse(it.date),
                    condition = WeatherCondition.fromWmo(it.weatherCode),
                    maxC = it.maxC,
                    minC = it.minC,
                )
            }
            .filter { !it.date.isBefore(today) },
        fetchedAt = Instant.ofEpochMilli(fetchedAtMillis),
    )
}
