package com.waynejiang.linefeed.core.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Weather(
    val locationName: String,
    val current: CurrentWeather,
    val daily: List<DailyForecast>,
    val fetchedAt: java.time.Instant,
)

data class CurrentWeather(
    val temperatureC: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val observedAt: LocalDateTime,
)

data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val maxC: Double,
    val minC: Double,
)

/**
 * Maps Open-Meteo's WMO weather codes (https://open-meteo.com/en/docs, `weather_code`) into a
 * small set of conditions the UI actually needs to distinguish (icon + label).
 */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
    ;

    companion object {
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67 -> RAIN
            in 71..77 -> SNOW
            80, 81, 82 -> SHOWERS
            85, 86 -> SNOW
            in 95..99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}
