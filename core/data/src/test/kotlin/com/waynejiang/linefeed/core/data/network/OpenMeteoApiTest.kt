package com.waynejiang.linefeed.core.data.network

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenMeteoApiTest {
    private val harness = MockApiTestHarness()
    private lateinit var api: OpenMeteoApi

    @Before
    fun setUp() {
        harness.start()
        api = harness.api()
    }

    @After
    fun tearDown() {
        harness.shutdown()
    }

    @Test
    fun `parses a real forecast fixture including parallel daily arrays`() = runTest {
        harness.enqueueJsonFromResource("fixtures/open_meteo_forecast.json")

        val forecast = api.getForecast()

        assertEquals("Asia/Taipei", forecast.timezone)
        assertEquals(28800, forecast.utcOffsetSeconds)
        assertTrue(forecast.current.temperature2m > -100)
        assertEquals(7, forecast.daily.time.size)
        assertEquals(forecast.daily.time.size, forecast.daily.weatherCode.size)
        assertEquals(forecast.daily.time.size, forecast.daily.temperature2mMax.size)
        assertEquals(forecast.daily.time.size, forecast.daily.temperature2mMin.size)
    }

    @Test
    fun `request carries taipei coordinates and timezone`() = runTest {
        harness.enqueueJsonFromResource("fixtures/open_meteo_forecast.json")

        api.getForecast()

        val request = harness.server.takeRequest()
        assertTrue(request.target.contains("latitude=25.033"))
        assertTrue(request.target.contains("longitude=121.5654"))
        assertTrue(request.target.contains("timezone=Asia%2FTaipei"))
        assertTrue(request.target.contains("forecast_days=7"))
    }
}
