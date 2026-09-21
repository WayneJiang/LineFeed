package com.waynejiang.linefeed.core.testing

import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.CurrentWeather
import com.waynejiang.linefeed.core.domain.model.DailyForecast
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.model.ServiceCard
import com.waynejiang.linefeed.core.domain.model.Weather
import com.waynejiang.linefeed.core.domain.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Small, deterministic builders for domain models so tests don't hand-roll boilerplate data classes. */
object TestData {
    fun article(
        id: Long,
        title: String = "Article $id",
        summary: String = "Summary $id",
        newsSite: String = "Test News",
        url: String = "https://example.com/$id",
        imageUrl: String? = "https://example.com/$id.jpg",
        publishedAt: Instant = Instant.parse("2026-09-21T00:00:00Z"),
        authors: List<String> = listOf("Author $id"),
        isBookmarked: Boolean = false,
    ) = Article(
        id = id,
        title = title,
        summary = summary,
        newsSite = newsSite,
        url = url,
        imageUrl = imageUrl,
        publishedAt = publishedAt,
        authors = authors,
        isBookmarked = isBookmarked,
    )

    fun feedArticle(
        id: Long,
        sortIndex: Long = id,
        publishedAt: Instant = Instant.parse("2026-09-21T00:00:00Z"),
        isBookmarked: Boolean = false,
    ) = FeedArticle(
        article = article(id = id, publishedAt = publishedAt, isBookmarked = isBookmarked),
        sortIndex = sortIndex,
    )

    fun weather(
        locationName: String = "Taipei",
        temperatureC: Double = 28.0,
        condition: WeatherCondition = WeatherCondition.CLEAR,
        fetchedAt: Instant = Instant.parse("2026-09-21T00:00:00Z"),
    ) = Weather(
        locationName = locationName,
        current = CurrentWeather(
            temperatureC = temperatureC,
            condition = condition,
            isDay = true,
            observedAt = LocalDateTime.of(2026, 9, 21, 10, 0),
        ),
        daily = listOf(
            DailyForecast(date = LocalDate.of(2026, 9, 21), condition = condition, maxC = temperatureC + 2, minC = temperatureC - 5),
        ),
        fetchedAt = fetchedAt,
    )

    fun serviceCard(
        id: Long,
        title: String = "Service $id",
        description: String = "Description $id",
        imageUrl: String? = "https://example.com/service-$id.jpg",
        ctaLabel: String = "Open",
        actionUrl: String = "https://example.com/service-$id",
    ) = ServiceCard(
        id = id,
        title = title,
        description = description,
        imageUrl = imageUrl,
        ctaLabel = ctaLabel,
        actionUrl = actionUrl,
    )
}
