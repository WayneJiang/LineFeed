package com.waynejiang.linefeed.feature.feed

import com.waynejiang.linefeed.core.domain.model.DailyForecast
import java.time.LocalDate

/** How many upcoming days the weather hero card's forecast row shows (PLAN.md: "今日、明日、一週天氣預報"). */
private const val MAX_UPCOMING_FORECAST_DAYS = 5

/**
 * Picks the days after [today] that [WeatherHeroCard]'s multi-day forecast row should render, sorted
 * chronologically and capped at [maxDays]. [today] must come from the caller's existing clock/`now`
 * source (see [com.waynejiang.linefeed.feature.feed.cells.WeatherHeroCard]'s `now` parameter) rather
 * than this function calling `LocalDate.now()` itself, so the selection stays deterministic and testable.
 *
 * [daily] may include today itself or stale/past dates (e.g. right after midnight before a refresh) —
 * both are filtered out, since this row is specifically "the days *after* today".
 */
fun selectUpcomingForecastDays(
    daily: List<DailyForecast>,
    today: LocalDate,
    maxDays: Int = MAX_UPCOMING_FORECAST_DAYS,
): List<DailyForecast> = daily
    .filter { it.date.isAfter(today) }
    .sortedBy { it.date }
    .take(maxDays)
