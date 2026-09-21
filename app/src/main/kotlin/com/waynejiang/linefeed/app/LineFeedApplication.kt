package com.waynejiang.linefeed.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's generated component root. Image-loader wiring (a shared OkHttpClient-backed
 * [coil3.SingletonImageLoader.Factory]) is added once `core:data`'s network stack exists (step 7+).
 */
@HiltAndroidApp
class LineFeedApplication : Application()
