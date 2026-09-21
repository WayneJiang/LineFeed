package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.data.network.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double = TAIPEI_LATITUDE,
        @Query("longitude") longitude: Double = TAIPEI_LONGITUDE,
        @Query("current") current: String = "temperature_2m,weather_code,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "Asia/Taipei",
        @Query("forecast_days") forecastDays: Int = 7,
    ): ForecastResponseDto

    companion object {
        // Weather is deliberately fixed to Taipei (PLAN.md §1.3): avoids a location-permission flow
        // that doesn't help the freshness/offline story this project is meant to demonstrate.
        const val TAIPEI_LATITUDE = 25.0330
        const val TAIPEI_LONGITUDE = 121.5654
    }
}
