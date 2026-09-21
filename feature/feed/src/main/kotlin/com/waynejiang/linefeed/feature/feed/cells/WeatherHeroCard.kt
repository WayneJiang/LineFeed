package com.waynejiang.linefeed.feature.feed.cells

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.waynejiang.linefeed.core.designsystem.format.RelativeTimeFormatter
import com.waynejiang.linefeed.core.designsystem.format.toUi
import com.waynejiang.linefeed.core.designsystem.theme.LineFeedTheme
import com.waynejiang.linefeed.core.domain.model.CurrentWeather
import com.waynejiang.linefeed.core.domain.model.DailyForecast
import com.waynejiang.linefeed.core.domain.model.Weather
import com.waynejiang.linefeed.core.domain.model.WeatherCondition
import com.waynejiang.linefeed.feature.feed.R
import com.waynejiang.linefeed.feature.feed.WeatherCardState
import com.waynejiang.linefeed.feature.feed.selectUpcomingForecastDays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The always-first, non-paged item in the feed (PLAN.md §5.5). Has its own loading/unavailable states, independent of the article list below it. */
@Composable
fun WeatherHeroCard(state: WeatherCardState, now: Instant, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LineFeedTheme.extraColors.weatherHero,
    ) {
        when (state) {
            WeatherCardState.Loading -> LoadingContent()
            WeatherCardState.Unavailable -> UnavailableContent()
            is WeatherCardState.Available -> AvailableContent(state, now)
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
    }
}

@Composable
private fun UnavailableContent() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feed_weather_unavailable),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AvailableContent(state: WeatherCardState.Available, now: Instant) {
    val ui = state.weather.current.condition.toUi()
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.weather.locationName, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${state.weather.current.temperatureC.roundToInt()}°",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.6f),
                )
                Text(stringResource(ui.labelRes), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(imageVector = ui.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        state.weather.daily.firstOrNull()?.let { today ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feed_weather_high_low, today.maxC.roundToInt(), today.minC.roundToInt()),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val upcoming = selectUpcomingForecastDays(state.weather.daily, today)
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))
            UpcomingForecastRow(upcoming)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.feed_weather_updated_ago,
                RelativeTimeFormatter.format(state.weather.fetchedAt, now),
            ),
            color = if (state.isOutdated) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The multi-day forecast row below the divider: today's H/L plus the next few days at a glance. */
@Composable
private fun UpcomingForecastRow(days: List<DailyForecast>) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { forecast ->
            ForecastDayItem(forecast, locale, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ForecastDayItem(forecast: DailyForecast, locale: Locale, modifier: Modifier = Modifier) {
    val ui = forecast.condition.toUi()
    val dayLabel = forecast.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val conditionLabel = stringResource(ui.labelRes)
    val maxTemp = forecast.maxC.roundToInt()
    val description = stringResource(R.string.feed_weather_forecast_day_content_description, dayLabel, conditionLabel, maxTemp)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = dayLabel, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Icon(imageVector = ui.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = "$maxTemp°", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun previewWeatherState(): WeatherCardState.Available {
    val fetchedAt = Instant.parse("2026-09-21T02:00:00Z")
    val today = LocalDate.of(2026, 9, 21)
    return WeatherCardState.Available(
        weather = Weather(
            locationName = "Taipei",
            current = CurrentWeather(
                temperatureC = 28.0,
                condition = WeatherCondition.CLEAR,
                isDay = true,
                observedAt = LocalDateTime.of(2026, 9, 21, 10, 0),
            ),
            daily = listOf(
                DailyForecast(date = today, condition = WeatherCondition.CLEAR, maxC = 30.0, minC = 24.0),
                DailyForecast(date = today.plusDays(1), condition = WeatherCondition.CLEAR, maxC = 35.0, minC = 26.0),
                DailyForecast(date = today.plusDays(2), condition = WeatherCondition.CLOUDY, maxC = 32.0, minC = 25.0),
                DailyForecast(date = today.plusDays(3), condition = WeatherCondition.RAIN, maxC = 29.0, minC = 23.0),
            ),
            fetchedAt = fetchedAt,
        ),
        isOutdated = false,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeatherHeroCardPreview() {
    LineFeedTheme {
        WeatherHeroCard(state = previewWeatherState(), now = Instant.parse("2026-09-21T02:05:00Z"))
    }
}
