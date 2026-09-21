package com.waynejiang.linefeed.feature.feed

import com.waynejiang.linefeed.core.domain.model.DailyForecast
import com.waynejiang.linefeed.core.domain.model.WeatherCondition
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingForecastSelectorTest {

    private fun day(offsetFromToday: Long, today: LocalDate) =
        DailyForecast(date = today.plusDays(offsetFromToday), condition = WeatherCondition.CLEAR, maxC = 30.0, minC = 20.0)

    @Test
    fun `seven days of data returns the five days after today`() {
        val today = LocalDate.of(2026, 9, 21)
        val daily = (0..6L).map { day(it, today) }

        val result = selectUpcomingForecastDays(daily, today)

        assertEquals(5, result.size)
        assertEquals((1..5L).map { today.plusDays(it) }, result.map { it.date })
    }

    @Test
    fun `only today's data returns an empty list`() {
        val today = LocalDate.of(2026, 9, 21)
        val daily = listOf(day(0, today))

        val result = selectUpcomingForecastDays(daily, today)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `past dates are filtered out`() {
        val today = LocalDate.of(2026, 9, 21)
        val daily = listOf(day(-2, today), day(-1, today), day(0, today), day(1, today), day(2, today))

        val result = selectUpcomingForecastDays(daily, today)

        assertEquals(listOf(today.plusDays(1), today.plusDays(2)), result.map { it.date })
    }

    @Test
    fun `results are sorted by date regardless of input order`() {
        val today = LocalDate.of(2026, 9, 21)
        val daily = listOf(day(3, today), day(1, today), day(2, today))

        val result = selectUpcomingForecastDays(daily, today)

        assertEquals(listOf(today.plusDays(1), today.plusDays(2), today.plusDays(3)), result.map { it.date })
    }

    @Test
    fun `caps at maxDays even when more upcoming days are available`() {
        val today = LocalDate.of(2026, 9, 21)
        val daily = (1..10L).map { day(it, today) }

        val result = selectUpcomingForecastDays(daily, today, maxDays = 3)

        assertEquals(listOf(today.plusDays(1), today.plusDays(2), today.plusDays(3)), result.map { it.date })
    }
}
