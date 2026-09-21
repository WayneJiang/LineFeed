package com.waynejiang.linefeed.core.data.di

import com.waynejiang.linefeed.core.data.network.ArticleRemoteDataSource
import com.waynejiang.linefeed.core.data.network.ConnectivityNetworkMonitor
import com.waynejiang.linefeed.core.data.network.RetrofitArticleRemoteDataSource
import com.waynejiang.linefeed.core.data.network.RetrofitServiceRemoteDataSource
import com.waynejiang.linefeed.core.data.network.RetrofitWeatherRemoteDataSource
import com.waynejiang.linefeed.core.data.network.ServiceRemoteDataSource
import com.waynejiang.linefeed.core.data.network.WeatherRemoteDataSource
import com.waynejiang.linefeed.core.data.refresh.DefaultFeedRefresher
import com.waynejiang.linefeed.core.data.refresh.SourceRefresher
import com.waynejiang.linefeed.core.data.repository.DefaultBookmarkRepository
import com.waynejiang.linefeed.core.data.repository.OfflineFirstArticleRepository
import com.waynejiang.linefeed.core.data.repository.OfflineFirstServiceCardRepository
import com.waynejiang.linefeed.core.data.repository.OfflineFirstWeatherRepository
import com.waynejiang.linefeed.core.data.time.SystemAppClock
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.refresh.FeedRefresher
import com.waynejiang.linefeed.core.domain.repository.ArticleRepository
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import com.waynejiang.linefeed.core.domain.repository.ServiceCardRepository
import com.waynejiang.linefeed.core.domain.repository.WeatherRepository
import com.waynejiang.linefeed.core.domain.time.AppClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Wires every repository/coordinator interface `core:domain` exposes to its single `core:data`
 * implementation. Kept as one module (not split per-feature) because these bindings are exactly
 * the seam PLAN.md §2 draws around "single source of truth" — there is deliberately only one
 * production implementation of each interface.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindArticleRepository(impl: OfflineFirstArticleRepository): ArticleRepository

    @Binds
    abstract fun bindWeatherRepository(impl: OfflineFirstWeatherRepository): WeatherRepository

    @Binds
    abstract fun bindServiceCardRepository(impl: OfflineFirstServiceCardRepository): ServiceCardRepository

    @Binds
    abstract fun bindBookmarkRepository(impl: DefaultBookmarkRepository): BookmarkRepository

    @Binds
    abstract fun bindFeedRefresher(impl: DefaultFeedRefresher): FeedRefresher

    @Binds
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun bindAppClock(impl: SystemAppClock): AppClock

    @Binds
    abstract fun bindArticleRemoteDataSource(impl: RetrofitArticleRemoteDataSource): ArticleRemoteDataSource

    @Binds
    abstract fun bindWeatherRemoteDataSource(impl: RetrofitWeatherRemoteDataSource): WeatherRemoteDataSource

    @Binds
    abstract fun bindServiceRemoteDataSource(impl: RetrofitServiceRemoteDataSource): ServiceRemoteDataSource

    // Only WEATHER and SERVICES: articles are refreshed exclusively by ArticleRemoteMediator (see
    // SourceRefresher's KDoc and PLAN.md §7.2).
    @Binds
    @IntoSet
    abstract fun bindWeatherSourceRefresher(impl: OfflineFirstWeatherRepository): SourceRefresher

    @Binds
    @IntoSet
    abstract fun bindServiceCardSourceRefresher(impl: OfflineFirstServiceCardRepository): SourceRefresher

    companion object {
        @Provides
        fun provideFreshnessPolicy(clock: AppClock): FreshnessPolicy = FreshnessPolicy(clock)
    }
}
