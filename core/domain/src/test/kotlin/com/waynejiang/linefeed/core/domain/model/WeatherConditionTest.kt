package com.waynejiang.linefeed.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionTest {
    @Test
    fun `maps representative wmo codes to conditions`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmo(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmo(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmo(2))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromWmo(3))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmo(45))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmo(48))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromWmo(53))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmo(63))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmo(73))
        assertEquals(WeatherCondition.SHOWERS, WeatherCondition.fromWmo(80))
        assertEquals(WeatherCondition.SHOWERS, WeatherCondition.fromWmo(82))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmo(85))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmo(86))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmo(95))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmo(99))
    }

    @Test
    fun `unknown code maps to UNKNOWN`() {
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(-1))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(1000))
    }
}
