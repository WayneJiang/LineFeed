package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.data.network.dto.ForecastResponseDto
import javax.inject.Inject

internal interface WeatherRemoteDataSource {
    suspend fun fetchForecast(): ForecastResponseDto
}

internal class RetrofitWeatherRemoteDataSource @Inject constructor(
    private val api: OpenMeteoApi,
) : WeatherRemoteDataSource {
    override suspend fun fetchForecast(): ForecastResponseDto = api.getForecast()
}
