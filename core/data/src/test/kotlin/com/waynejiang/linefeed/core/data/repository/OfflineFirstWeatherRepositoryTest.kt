package com.waynejiang.linefeed.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.network.WeatherRemoteDataSource
import com.waynejiang.linefeed.core.data.network.dto.CurrentDto
import com.waynejiang.linefeed.core.data.network.dto.DailyDto
import com.waynejiang.linefeed.core.data.network.dto.ForecastResponseDto
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineFirstWeatherRepositoryTest {
    private lateinit var database: LineFeedDatabase
    private val clock = FakeClock()

    private fun forecast() = ForecastResponseDto(
        timezone = "Asia/Taipei",
        utcOffsetSeconds = 28800,
        current = CurrentDto(time = "2026-09-21T10:15", temperature2m = 28.0, weatherCode = 0, isDay = 1),
        daily = DailyDto(
            time = listOf("2026-09-21", "2026-09-22"),
            weatherCode = listOf(0, 1),
            temperature2mMax = listOf(30.0, 31.0),
            temperature2mMin = listOf(24.0, 25.0),
        ),
    )

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(remoteDataSource: WeatherRemoteDataSource) = OfflineFirstWeatherRepository(
        weatherDao = database.weatherDao(),
        remoteDataSource = remoteDataSource,
        syncMetadataDao = database.syncMetadataDao(),
        clock = clock,
    )

    @Test
    fun `observeWeather is null before any refresh`() = runBlocking {
        assertNull(repository(fakeSource(forecast())).observeWeather().first())
    }

    @Test
    fun `refresh stores the forecast, marks success and observeWeather reflects it`() = runBlocking {
        val repo = repository(fakeSource(forecast()))

        val result = repo.refresh()

        assertEquals(SourceResult.Success, result)
        val weather = repo.observeWeather().first()
        assertEquals("Taipei", weather?.locationName)
        assertEquals(28.0, weather?.current?.temperatureC)
        assertEquals(clock.now().toEpochMilli(), database.syncMetadataDao().get(ContentSource.WEATHER.name)?.lastSuccessAtMillis)
    }

    @Test
    fun `refresh failure returns Failed and records sync failure without touching the cache`() = runBlocking {
        val failing = object : WeatherRemoteDataSource {
            override suspend fun fetchForecast() = throw java.io.IOException("boom")
        }
        val result = repository(failing).refresh()

        assertTrue(result is SourceResult.Failed)
        assertNull(database.syncMetadataDao().get(ContentSource.WEATHER.name)?.lastSuccessAtMillis)
        assertEquals("UNKNOWN", database.syncMetadataDao().get(ContentSource.WEATHER.name)?.lastError)
    }

    private fun fakeSource(response: ForecastResponseDto) = object : WeatherRemoteDataSource {
        override suspend fun fetchForecast(): ForecastResponseDto = response
    }
}
