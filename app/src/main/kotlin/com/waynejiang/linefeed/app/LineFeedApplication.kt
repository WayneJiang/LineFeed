package com.waynejiang.linefeed.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt's generated component root. Image-loader wiring (a shared OkHttpClient-backed
 * [coil3.SingletonImageLoader.Factory]) is added once `feature:feed` exists (step 9+).
 */
@HiltAndroidApp
class LineFeedApplication : Application() {

    @Inject
    lateinit var appRefreshInitializer: AppRefreshInitializer

    override fun onCreate() {
        super.onCreate()
        appRefreshInitializer.start()
    }
}
