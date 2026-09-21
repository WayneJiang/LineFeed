package com.waynejiang.linefeed.core.data.refresh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.testing.FakeClock
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DefaultFeedRefresherTest {
    private lateinit var database: LineFeedDatabase
    private val clock = FakeClock()
    private val networkMonitor = FakeNetworkMonitor(NetworkStatus.UNMETERED)
    // Unconfined so the SingleFlight's internally-launched coroutine actually runs within a plain runBlocking test body.
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private class RecordingRefresher(override val source: ContentSource, private val result: () -> SourceResult) : SourceRefresher {
        var callCount = 0
            private set

        override suspend fun refresh(): SourceResult {
            callCount++
            return result()
        }
    }

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun refresher(vararg sources: SourceRefresher) = DefaultFeedRefresher(
        freshnessPolicy = FreshnessPolicy(clock),
        networkMonitor = networkMonitor,
        syncMetadataDao = database.syncMetadataDao(),
        refreshers = sources.toSet(),
        scope = scope,
        clock = clock,
    )

    @Test
    fun `USER_PULL fetches every source regardless of freshness`() = runBlocking {
        val weather = RecordingRefresher(ContentSource.WEATHER) { SourceResult.Success }
        val services = RecordingRefresher(ContentSource.SERVICES) { SourceResult.Success }
        database.syncMetadataDao().markSuccess(ContentSource.WEATHER.name, clock.now().toEpochMilli())
        database.syncMetadataDao().markSuccess(ContentSource.SERVICES.name, clock.now().toEpochMilli())

        val report = refresher(weather, services).refresh(RefreshTrigger.USER_PULL)

        assertEquals(1, weather.callCount)
        assertEquals(1, services.callCount)
        assertEquals(SourceResult.Success, report.results[ContentSource.WEATHER])
    }

    @Test
    fun `FOREGROUND skips a source that is still fresh`() = runBlocking {
        val weather = RecordingRefresher(ContentSource.WEATHER) { SourceResult.Success }
        database.syncMetadataDao().markSuccess(ContentSource.WEATHER.name, clock.now().toEpochMilli())

        val report = refresher(weather).refresh(RefreshTrigger.FOREGROUND)

        assertEquals(0, weather.callCount)
        assertTrue(report.results[ContentSource.WEATHER] is SourceResult.Skipped)
    }

    @Test
    fun `FOREGROUND fetches a source once its TTL has elapsed`() = runBlocking {
        val weather = RecordingRefresher(ContentSource.WEATHER) { SourceResult.Success }
        database.syncMetadataDao().markSuccess(ContentSource.WEATHER.name, clock.now().toEpochMilli())
        clock.advanceBy(Duration.ofMinutes(20))

        refresher(weather).refresh(RefreshTrigger.FOREGROUND)

        assertEquals(1, weather.callCount)
    }

    @Test
    fun `one source failing does not prevent the other from refreshing`() = runBlocking {
        val weather = RecordingRefresher(ContentSource.WEATHER) { SourceResult.Failed(com.waynejiang.linefeed.core.domain.model.AppError.SERVER) }
        val services = RecordingRefresher(ContentSource.SERVICES) { SourceResult.Success }

        val report = refresher(weather, services).refresh(RefreshTrigger.USER_PULL)

        assertTrue(report.results[ContentSource.WEATHER] is SourceResult.Failed)
        assertEquals(SourceResult.Success, report.results[ContentSource.SERVICES])
    }

    @Test
    fun `offline network skips every source without calling refresh`() = runBlocking {
        networkMonitor.set(NetworkStatus.OFFLINE)
        val weather = RecordingRefresher(ContentSource.WEATHER) { SourceResult.Success }

        val report = refresher(weather).refresh(RefreshTrigger.USER_PULL)

        assertEquals(0, weather.callCount)
        assertTrue(report.results[ContentSource.WEATHER] is SourceResult.Skipped)
    }

    @Test
    fun `FOREGROUND bumps articleRefreshRequestId when articles are stale`() = runBlocking {
        val refresher = refresher()

        refresher.refresh(RefreshTrigger.FOREGROUND)

        assertEquals(1L, refresher.status.value.articleRefreshRequestId)
    }

    @Test
    fun `FOREGROUND does not bump articleRefreshRequestId when articles are fresh`() = runBlocking {
        database.syncMetadataDao().markSuccess(ContentSource.ARTICLES.name, clock.now().toEpochMilli())
        val refresher = refresher()

        refresher.refresh(RefreshTrigger.FOREGROUND)

        assertEquals(0L, refresher.status.value.articleRefreshRequestId)
    }

    @Test
    fun `USER_PULL never bumps articleRefreshRequestId (UI drives it directly)`() = runBlocking {
        val refresher = refresher()

        refresher.refresh(RefreshTrigger.USER_PULL)

        assertEquals(0L, refresher.status.value.articleRefreshRequestId)
    }

    @Test
    fun `status inFlight is cleared after refresh completes`() = runBlocking {
        val weather = RecordingRefresher(ContentSource.WEATHER) { SourceResult.Success }

        refresher(weather).let { r ->
            r.refresh(RefreshTrigger.USER_PULL)
            assertFalse(r.status.value.inFlight.contains(ContentSource.WEATHER))
        }
    }
}
