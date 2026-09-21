package com.waynejiang.linefeed.core.data.network

import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/** Shared MockWebServer + Retrofit wiring, matching [com.waynejiang.linefeed.core.data.di.NetworkModule]'s Json config. */
internal class MockApiTestHarness {
    val server = MockWebServer()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    inline fun <reified T> api(): T = retrofit.create()

    fun start() = server.start()

    fun shutdown() = server.close()

    fun enqueueJsonFromResource(resourcePath: String) {
        val body = requireNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
            "Missing test fixture: $resourcePath"
        }.bufferedReader().readText()
        server.enqueue(mockwebserver3.MockResponse(code = 200, body = body))
    }
}
