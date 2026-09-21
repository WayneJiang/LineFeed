package com.waynejiang.linefeed.feature.saved

import app.cash.turbine.test
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.testing.FakeBookmarkRepository
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import com.waynejiang.linefeed.core.testing.MainDispatcherRule
import com.waynejiang.linefeed.core.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Query/search is not implemented until PLAN.md §10 step 13 — this only covers the plain saved list. */
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
}
