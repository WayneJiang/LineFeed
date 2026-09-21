package com.waynejiang.linefeed.core.data.repository

import com.waynejiang.linefeed.core.data.database.dao.SyncMetadataDao
import com.waynejiang.linefeed.core.data.database.dao.WeatherDao
import com.waynejiang.linefeed.core.data.mapper.toAppError
import com.waynejiang.linefeed.core.data.mapper.toDomain
import com.waynejiang.linefeed.core.data.mapper.toEntity
import com.waynejiang.linefeed.core.data.network.WeatherRemoteDataSource
import com.waynejiang.linefeed.core.data.refresh.SourceRefresher
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.Weather
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.domain.repository.WeatherRepository
import com.waynejiang.linefeed.core.domain.time.AppClock
import com.waynejiang.linefeed.core.domain.util.suspendRunCatching
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room is the only thing the UI reads from ([observeWeather]); [refresh] is only ever called by
 * `DefaultFeedRefresher` once the shared [com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy]
 * has already decided a fetch is due, so this class never checks freshness itself.
 */
internal class OfflineFirstWeatherRepository @Inject constructor(
    private val weatherDao: WeatherDao,
    private val remoteDataSource: WeatherRemoteDataSource,
    private val syncMetadataDao: SyncMetadataDao,
    private val clock: AppClock,
) : WeatherRepository, SourceRefresher {

    override val source: ContentSource = ContentSource.WEATHER

    override fun observeWeather(): Flow<Weather?> = weatherDao.observe().map { entity ->
        entity?.toDomain(today = LocalDate.ofInstant(clock.now(), ZoneId.systemDefault()))
    }

    override suspend fun refresh(): SourceResult {
        val now = clock.now()
        return suspendRunCatching {
            val forecast = remoteDataSource.fetchForecast()
            weatherDao.upsert(forecast.toEntity(fetchedAt = now))
            syncMetadataDao.markSuccess(source.name, now.toEpochMilli())
        }.fold(
            onSuccess = { SourceResult.Success },
            onFailure = { error ->
                syncMetadataDao.markFailure(source.name, now.toEpochMilli(), error.toAppError().name)
                SourceResult.Failed(error.toAppError())
            },
        )
    }
}
