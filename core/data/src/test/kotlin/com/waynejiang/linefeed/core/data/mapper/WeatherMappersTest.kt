package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.data.network.dto.CurrentDto
import com.waynejiang.linefeed.core.data.network.dto.DailyDto
import com.waynejiang.linefeed.core.data.network.dto.ForecastResponseDto
import com.waynejiang.linefeed.core.domain.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherMappersTest {
    private val fetchedAt: Instant = Instant.parse("2026-09-21T10:00:00Z")

    private fun forecast(
        dailyTime: List<String> = listOf("2026-09-20", "2026-09-21", "2026-09-22"),
        weatherCode: List<Int> = listOf(3, 0, 61),
        max: List<Double> = listOf(30.0, 31.0, 29.0),
        min: List<Double> = listOf(20.0, 21.0, 19.0),
    ) = ForecastResponseDto(
        timezone = "Asia/Taipei",
        utcOffsetSeconds = 28800,
        current = CurrentDto(time = "2026-09-21T10:15", temperature2m = 29.9, weatherCode = 0, isDay = 1),
        daily = DailyDto(time = dailyTime, weatherCode = weatherCode, temperature2mMax = max, temperature2mMin = min),
    )

    @Test
    fun `zips parallel daily arrays correctly`() {
        val entity = forecast().toEntity(fetchedAt)
        val domain = entity.toDomain(today = LocalDate.parse("2026-09-20"))

        assertEquals(3, domain.daily.size)
        assertEquals(LocalDate.parse("2026-09-20"), domain.daily[0].date)
        assertEquals(WeatherCondition.CLOUDY, domain.daily[0].condition)
        assertEquals(30.0, domain.daily[0].maxC, 0.0)
        assertEquals(20.0, domain.daily[0].minC, 0.0)
    }

    @Test
    fun `mismatched parallel array lengths take the shortest without crashing`() {
        val entity = forecast(
            dailyTime = listOf("2026-09-20", "2026-09-21", "2026-09-22"),
            weatherCode = listOf(3, 0),
            max = listOf(30.0, 31.0, 29.0),
            min = listOf(20.0),
        ).toEntity(fetchedAt)

        val domain = entity.toDomain(today = LocalDate.parse("2026-01-01"))

        assertEquals(1, domain.daily.size)
    }

    @Test
    fun `days before today are filtered out`() {
        val entity = forecast().toEntity(fetchedAt)
        val domain = entity.toDomain(today = LocalDate.parse("2026-09-22"))

        assertEquals(listOf(LocalDate.parse("2026-09-22")), domain.daily.map { it.date })
    }

    @Test
    fun `local time without timezone is parsed as-is`() {
        val entity = forecast().toEntity(fetchedAt)
        val domain = entity.toDomain(today = LocalDate.parse("2026-01-01"))

        assertEquals(2026, domain.current.observedAt.year)
        assertEquals(10, domain.current.observedAt.hour)
        assertEquals(15, domain.current.observedAt.minute)
        assertTrue(domain.current.isDay)
    }
}
