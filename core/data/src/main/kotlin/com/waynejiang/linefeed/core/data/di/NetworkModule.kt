package com.waynejiang.linefeed.core.data.di

import android.content.Context
import com.waynejiang.linefeed.core.data.BuildConfig
import com.waynejiang.linefeed.core.data.network.DummyJsonApi
import com.waynejiang.linefeed.core.data.network.OpenMeteoApi
import com.waynejiang.linefeed.core.data.network.SpaceflightApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

private const val SPACEFLIGHT_BASE_URL = "https://api.spaceflightnewsapi.net/"
private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
private const val DUMMY_JSON_BASE_URL = "https://dummyjson.com/"
private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    // `ignoreUnknownKeys`: both Spaceflight and DummyJSON add fields over time (PLAN.md §6.1/§6.3);
    // `explicitNulls = false`/`coerceInputValues`: several observed fields are absent rather than
    // explicitly null (e.g. DummyJSON's `brand`).
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES)
        return OkHttpClient.Builder()
            .cache(cache)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    @SpaceflightRetrofit
    fun provideSpaceflightRetrofit(client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(SPACEFLIGHT_BASE_URL, client, json)

    @Provides
    @Singleton
    @OpenMeteoRetrofit
    fun provideOpenMeteoRetrofit(client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(OPEN_METEO_BASE_URL, client, json)

    @Provides
    @Singleton
    @DummyJsonRetrofit
    fun provideDummyJsonRetrofit(client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(DUMMY_JSON_BASE_URL, client, json)

    @Provides
    @Singleton
    fun provideSpaceflightApi(@SpaceflightRetrofit retrofit: Retrofit): SpaceflightApi = retrofit.create()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(@OpenMeteoRetrofit retrofit: Retrofit): OpenMeteoApi = retrofit.create()

    @Provides
    @Singleton
    fun provideDummyJsonApi(@DummyJsonRetrofit retrofit: Retrofit): DummyJsonApi = retrofit.create()

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
