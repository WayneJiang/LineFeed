package com.waynejiang.linefeed.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Open-Meteo forecast response (PLAN.md §6.2). `daily` fields are parallel arrays keyed by index. */
@Serializable
data class ForecastResponseDto(
    val timezone: String,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int,
    val current: CurrentDto,
    val daily: DailyDto,
)

/** `time` is local (no timezone/offset suffix), e.g. `"2026-09-21T10:15"`. */
@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
)

@Serializable
data class DailyDto(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double> = emptyList(),
)
