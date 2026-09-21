package com.waynejiang.linefeed.feature.feed

import com.waynejiang.linefeed.core.domain.model.Weather

/** The weather hero card's own state machine, independent of the paged article list (PLAN.md §5.2). */
sealed interface WeatherCardState {
    data object Loading : WeatherCardState
    data object Unavailable : WeatherCardState
    data class Available(val weather: Weather, val isOutdated: Boolean) : WeatherCardState
}
