package com.waynejiang.linefeed.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

/** Hilt's generated component root; also the Coil [SingletonImageLoader.Factory] so every `FeedImage` shares one OkHttpClient (and its disk cache) with the rest of the network stack. */
@HiltAndroidApp
class LineFeedApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var appRefreshInitializer: AppRefreshInitializer

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        appRefreshInitializer.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
        .crossfade(true)
        .build()
}
