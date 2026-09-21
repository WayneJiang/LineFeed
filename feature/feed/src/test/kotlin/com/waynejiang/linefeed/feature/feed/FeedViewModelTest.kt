package com.waynejiang.linefeed.feature.feed

import app.cash.turbine.test
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.AppError
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.refresh.RefreshReport
import com.waynejiang.linefeed.core.domain.refresh.RefreshStatus
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.testing.FakeArticleRepository
import com.waynejiang.linefeed.core.testing.FakeBookmarkRepository
import com.waynejiang.linefeed.core.testing.FakeClock
import com.waynejiang.linefeed.core.testing.FakeFeedRefresher
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import com.waynejiang.linefeed.core.testing.FakeServiceCardRepository
import com.waynejiang.linefeed.core.testing.FakeWeatherRepository
import com.waynejiang.linefeed.core.testing.MainDispatcherRule
import com.waynejiang.linefeed.core.testing.TestData
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock()
    private val articleRepository = FakeArticleRepository()
    private val weatherRepository = FakeWeatherRepository()
    private val serviceCardRepository = FakeServiceCardRepository()
    private val bookmarkRepository = FakeBookmarkRepository()
    private val feedRefresher = FakeFeedRefresher()
    private val networkMonitor = FakeNetworkMonitor(NetworkStatus.UNMETERED)
    private val freshnessPolicy = FreshnessPolicy(clock)

    private fun viewModel() = FeedViewModel(
        articleRepository = articleRepository,
        weatherRepository = weatherRepository,
        serviceCardRepository = serviceCardRepository,
        bookmarkRepository = bookmarkRepository,
        feedRefresher = feedRefresher,
        networkMonitor = networkMonitor,
        freshnessPolicy = freshnessPolicy,
    )

    @Test
    fun `weather is Loading until data or a failure arrives`() = runTest {
        viewModel().uiState.test {
            assertEquals(WeatherCardState.Loading, awaitItem().weather)
        }
    }

    @Test
    fun `weather becomes Available once the repository emits it, outdated flag from FreshnessPolicy`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(WeatherCardState.Loading, awaitItem().weather)

            weatherRepository.setWeather(TestData.weather(fetchedAt = clock.now()))
            val fresh = awaitItem().weather as WeatherCardState.Available
            assertEquals(false, fresh.isOutdated)

            // A distinct (older) fetchedAt than the "fresh" case above: StateFlow only re-emits on
            // a value that's structurally different, so this must not collide with `clock.now()` above.
            val outdatedFetchedAt = clock.now().minus(Duration.ofHours(4))
            clock.advanceBy(Duration.ofHours(4))
            weatherRepository.setWeather(TestData.weather(fetchedAt = outdatedFetchedAt))
            val outdated = awaitItem().weather as WeatherCardState.Available
            assertTrue(outdated.isOutdated)
        }
    }

    @Test
    fun `weather becomes Unavailable when null and the last refresh failed`() = runTest {
        viewModel()
        feedRefresher.setStatus(RefreshStatus(lastResults = mapOf(ContentSource.WEATHER to SourceResult.Failed(AppError.SERVER))))

        viewModel().uiState.test {
            assertEquals(WeatherCardState.Unavailable, awaitItem().weather)
        }
    }

    @Test
    fun `isOffline follows the network monitor`() = runTest {
        networkMonitor.set(NetworkStatus.OFFLINE)
        viewModel().uiState.test {
            assertTrue(awaitItem().isOffline)
        }
    }

    @Test
    fun `pendingArticleRefreshId surfaces once and clears after onArticleRefreshHandled`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertNull(awaitItem().pendingArticleRefreshId)

            feedRefresher.setStatus(RefreshStatus(articleRefreshRequestId = 1))
            assertEquals(1L, awaitItem().pendingArticleRefreshId)

            vm.onArticleRefreshHandled(1)
            assertNull(awaitItem().pendingArticleRefreshId)
        }
    }

    @Test
    fun `onToggleBookmark flips the current bookmark state`() = runTest {
        val vm = viewModel()
        val article = TestData.article(id = 1, isBookmarked = false)

        vm.onToggleBookmark(article)

        assertEquals(setOf(1L), bookmarkRepository.observeBookmarkedIds().first())
    }

    @Test
    fun `onPullToRefresh triggers a USER_PULL refresh`() = runTest {
        val vm = viewModel()
        vm.onPullToRefresh()
        assertEquals(listOf(RefreshTrigger.USER_PULL), feedRefresher.triggers)
    }

    @Test
    fun `onPullToRefresh surfaces a user message when a source fails`() = runTest {
        feedRefresher.nextReport = RefreshReport(mapOf(ContentSource.WEATHER to SourceResult.Failed(AppError.SERVER)))
        val vm = viewModel()

        vm.uiState.test {
            assertNull(awaitItem().userMessage)
            vm.onPullToRefresh()
            val message = awaitItem().userMessage
            assertEquals(R.string.feed_refresh_failed, message?.messageRes)

            vm.onUserMessageShown(message!!.id)
            assertNull(awaitItem().userMessage)
        }
    }

    // `feedItems` itself (the two `cachedIn(viewModelScope)` calls + `combine`) is intentionally not
    // exercised with `asSnapshot()` here: `viewModelScope`'s Job is a separate coroutine hierarchy
    // from `runTest`'s `TestScope`, and both FakeArticleRepository/FakeServiceCardRepository are
    // backed by never-completing `MutableStateFlow`s, so asking `asSnapshot()` to fully settle
    // through a real `cachedIn` hangs the test with `UncompletedCoroutinesError` (a testing-library
    // sharp edge, not a production bug — see docs/NOTES.md). The `combine(...) { ... toFeedItems(...) }`
    // expression itself is fully covered by FeedPagingTransformsTest.
}
