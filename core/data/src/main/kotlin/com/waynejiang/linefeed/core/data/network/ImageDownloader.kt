package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.domain.util.suspendRunCatching
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads one bookmarked article's image to [destination] for offline reading (PLAN.md §10 step 12). */
internal interface ImageDownloader {
    /** Returns whether [destination] now holds the downloaded image. */
    suspend fun download(url: String, destination: File): Boolean
}

/**
 * Writes to a `.tmp` sibling file first, then renames onto [destination] only once the whole body
 * has been read successfully — a reader can never observe a half-written image file, and a
 * process death mid-download leaves only an orphaned `.tmp` file, never a corrupt "real" one.
 */
internal class OkHttpImageDownloader @Inject constructor(
    private val client: OkHttpClient,
) : ImageDownloader {
    override suspend fun download(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        suspendRunCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body
                destination.parentFile?.mkdirs()
                val tmp = File(destination.parentFile, "${destination.name}.tmp")
                tmp.outputStream().use { output -> body.byteStream().copyTo(output) }
                tmp.renameTo(destination)
            }
        }.getOrDefault(false)
    }
}
