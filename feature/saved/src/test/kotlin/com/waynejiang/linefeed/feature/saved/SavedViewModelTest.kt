package com.waynejiang.linefeed.feature.saved

import app.cash.turbine.test
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.testing.FakeBookmarkRepository
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import com.waynejiang.linefeed.core.testing.MainDispatcherRule
import com.waynejiang.linefeed.core.testing.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bookmarkRepository = FakeBookmarkRepository()
    private val networkMonitor = FakeNetworkMonitor(NetworkStatus.UNMETERED)

    private fun viewModel() = SavedViewModel(bookmarkRepository = bookmarkRepository, networkMonitor = networkMonitor)

    @Test
    fun `starts loading before the first emission`() {
        assertTrue(viewModel().uiState.value.isLoading)
    }

    @Test
    fun `becomes not-loading with an empty list when nothing is saved`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.items.isEmpty())
        }
    }

    @Test
    fun `reflects saved articles ordered as the repository provides them`() = runTest {
        val article = TestData.article(id = 1, title = "Rocket launch")
        bookmarkRepository.setSaved(listOf(SavedArticle(article = article, savedAt = article.publishedAt, localImagePath = null)))

        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(listOf(1L), state.items.map { it.article.id })
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
    fun `onRemoveBookmark removes the article from the saved list`() = runTest {
        val article = TestData.article(id = 1)
        bookmarkRepository.setBookmarked(article, bookmarked = true)
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(listOf(1L), awaitItem().items.map { it.article.id })
            vm.onRemoveBookmark(article)
            assertTrue(awaitItem().items.isEmpty())
        }
    }

    @Test
    fun `an empty query on cold start does not wait for the debounce`() = runTest {
        bookmarkRepository.setBookmarked(TestData.article(id = 1), bookmarked = true)

        // A flat `debounce(300)` would delay even this very first, blank-query emission; the
        // per-value debounce in SavedViewModel special-cases a blank query to 0ms specifically so
        // cold start isn't held up 300ms behind an empty search field.
        viewModel().uiState.test {
            assertEquals(listOf(1L), awaitItem().items.map { it.article.id })
        }
    }

    // Runs Dispatchers.Main on this test's own `testScheduler` (rather than
    // MainDispatcherRule's default, separate UnconfinedTestDispatcher) so `advanceTimeBy` here
    // actually controls the debounce's `delay()` on viewModelScope. Collects into a plain list
    // instead of Turbine: a StateFlow collector always sees an initial default value before any
    // real computation lands, and how many distinct emissions arrive on the way to the debounced
    // result is an implementation detail — asserting on "the latest known state at this point in
    // virtual time" is robust to that, where Turbine's strict one-at-a-time awaitItem() isn't.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onQueryChange updates the query field immediately but filters the list only after the debounce settles`() = runTest {
        // Deliberately no matching Dispatchers.resetMain() here: doing that mid-test races with
        // runTest's own end-of-test cleanup of still-alive children (the ViewModel's internal
        // stateIn/WhileSubscribed sharing coroutine is never explicitly stopped — nothing calls
        // ViewModel.onCleared() in a plain unit test) and crashes with "Dispatchers.Main was
        // accessed ... test dispatcher was unset". MainDispatcherRule's own finished() already
        // resets Main exactly once, after this method fully returns; let it do that.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        bookmarkRepository.setBookmarked(TestData.article(id = 1, title = "Rocket launch"), bookmarked = true)
        bookmarkRepository.setBookmarked(TestData.article(id = 2, title = "Satellite deployed"), bookmarked = true)
        val vm = viewModel()
        val states = mutableListOf<SavedUiState>()
        val job = launch { vm.uiState.collect { states += it } }

        advanceUntilIdle()
        assertEquals(2, states.last().items.size)

        vm.onQueryChange("rocket")
        runCurrent()
        assertEquals("rocket", states.last().query)
        assertEquals(2, states.last().items.size) // debounce hasn't fired yet

        advanceTimeBy(299)
        assertEquals(2, states.last().items.size) // still hasn't, one ms short

        advanceTimeBy(50)
        assertEquals(listOf(1L), states.last().items.map { it.article.id })

        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rapid typing only queries once, for the final value`() = runTest {
        // See the comment on the previous test: no matching Dispatchers.resetMain() here on purpose.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // A second, non-matching article: with only one bookmark whose title contains every
        // prefix of "rocket", the unfiltered and filtered lists would be identical and this test
        // couldn't tell "filtered once" apart from "filtered after every keystroke".
        bookmarkRepository.setBookmarked(TestData.article(id = 1, title = "Rocket launch"), bookmarked = true)
        bookmarkRepository.setBookmarked(TestData.article(id = 2, title = "Satellite deployed"), bookmarked = true)
        val vm = viewModel()
        val states = mutableListOf<SavedUiState>()
        val job = launch { vm.uiState.collect { states += it } }
        advanceUntilIdle()
        val emissionsBeforeTyping = states.size

        vm.onQueryChange("r")
        advanceTimeBy(100)
        vm.onQueryChange("ro")
        advanceTimeBy(100)
        vm.onQueryChange("rocket")
        advanceTimeBy(300)
        runCurrent()

        // Only the final, settled query ever reaches observeSaved(): "r" and "ro" each had
        // another keystroke land before their 300ms debounce could fire.
        assertEquals(listOf(1L), states.last().items.map { it.article.id })
        val queriesSeenByTheRepository = states.drop(emissionsBeforeTyping).map { it.items }.distinct()
        assertEquals(2, queriesSeenByTheRepository.size) // the unfiltered list, then the "rocket" filter — nothing in between

        job.cancel()
    }

    @Test
    fun `onQueryChange with no results still leaves the list observable as empty`() = runTest {
        bookmarkRepository.setBookmarked(TestData.article(id = 1, title = "Rocket launch"), bookmarked = true)
        val vm = viewModel()

        vm.onQueryChange("nonexistent")

        vm.uiState.test {
            assertTrue(awaitItem().items.isEmpty())
        }
    }
}
