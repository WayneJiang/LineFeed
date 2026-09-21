package com.waynejiang.linefeed.feature.feed.cells

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.waynejiang.linefeed.core.designsystem.format.RelativeTimeFormatter
import com.waynejiang.linefeed.core.designsystem.format.toUi
import com.waynejiang.linefeed.core.designsystem.theme.LineFeedTheme
import com.waynejiang.linefeed.feature.feed.R
import com.waynejiang.linefeed.feature.feed.WeatherCardState
import java.time.Instant
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
