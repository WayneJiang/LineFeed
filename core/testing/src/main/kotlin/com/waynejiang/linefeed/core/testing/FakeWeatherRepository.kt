package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.model.Weather
import com.waynejiang.linefeed.core.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWeatherRepository(initial: Weather? = null) : WeatherRepository {
    private val weather = MutableStateFlow(initial)

    fun setWeather(value: Weather?) {
        weather.value = value
    }

    override fun observeWeather(): Flow<Weather?> = weather
}
