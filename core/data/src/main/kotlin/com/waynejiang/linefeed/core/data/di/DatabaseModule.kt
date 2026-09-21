package com.waynejiang.linefeed.core.data.di

import android.content.Context
import androidx.room.Room
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.dao.BookmarkDao
import com.waynejiang.linefeed.core.data.database.dao.FeedArticleDao
import com.waynejiang.linefeed.core.data.database.dao.RemoteKeyDao
import com.waynejiang.linefeed.core.data.database.dao.ServiceCardDao
import com.waynejiang.linefeed.core.data.database.dao.SyncMetadataDao
import com.waynejiang.linefeed.core.data.database.dao.WeatherDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATABASE_NAME = "linefeed.db"

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LineFeedDatabase =
        Room.databaseBuilder(context, LineFeedDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun provideFeedArticleDao(database: LineFeedDatabase): FeedArticleDao = database.feedArticleDao()

    @Provides
    fun provideRemoteKeyDao(database: LineFeedDatabase): RemoteKeyDao = database.remoteKeyDao()

    @Provides
    fun provideBookmarkDao(database: LineFeedDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideWeatherDao(database: LineFeedDatabase): WeatherDao = database.weatherDao()

    @Provides
    fun provideServiceCardDao(database: LineFeedDatabase): ServiceCardDao = database.serviceCardDao()

    @Provides
    fun provideSyncMetadataDao(database: LineFeedDatabase): SyncMetadataDao = database.syncMetadataDao()
}
