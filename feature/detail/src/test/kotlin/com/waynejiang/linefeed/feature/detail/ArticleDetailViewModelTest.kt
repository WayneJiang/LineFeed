package com.waynejiang.linefeed.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.testing.FakeArticleRepository
import com.waynejiang.linefeed.core.testing.FakeBookmarkRepository
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import com.waynejiang.linefeed.core.testing.MainDispatcherRule
import com.waynejiang.linefeed.core.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArticleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val articleRepository = FakeArticleRepository()
    private val bookmarkRepository = FakeBookmarkRepository()
    private val networkMonitor = FakeNetworkMonitor(NetworkStatus.UNMETERED)

    private fun viewModel(articleId: Long = 1L) = ArticleDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("articleId" to articleId)),
        articleRepository = articleRepository,
        bookmarkRepository = bookmarkRepository,
        networkMonitor = networkMonitor,
    )

    @Test
    fun `NotFound when the article is neither cached nor saved`() = runTest {
        viewModel().uiState.test {
            assertEquals(DetailUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `Content from the feed cache reflects offline status`() = runTest {
        articleRepository.setFeed(listOf(TestData.feedArticle(id = 1)))
        networkMonitor.set(NetworkStatus.OFFLINE)

        viewModel(articleId = 1).uiState.test {
            val content = awaitItem() as DetailUiState.Content
            assertEquals(1L, content.article.id)
            assertTrue(content.isOffline)
        }
    }

    @Test
    fun `Content falls back to the saved snapshot when the feed cache no longer has it`() = runTest {
        val article = TestData.article(id = 1, title = "Cached elsewhere")
        bookmarkRepository.setBookmarked(article, bookmarked = true)

        viewModel(articleId = 1).uiState.test {
            val content = awaitItem() as DetailUiState.Content
            assertEquals("Cached elsewhere", content.article.title)
            assertTrue(content.article.isBookmarked)
        }
    }

    @Test
    fun `localImagePath comes from the saved snapshot, not the feed cache`() = runTest {
        articleRepository.setFeed(listOf(TestData.feedArticle(id = 1)))
        val article = TestData.article(id = 1, isBookmarked = true)
        bookmarkRepository.setSaved(listOf(SavedArticle(article = article, savedAt = article.publishedAt, localImagePath = "/data/images/1.jpg")))

        viewModel(articleId = 1).uiState.test {
            val content = awaitItem() as DetailUiState.Content
            assertEquals("/data/images/1.jpg", content.localImagePath)
        }
    }

    @Test
    fun `isBookmarked is false when only present in the feed cache`() = runTest {
        articleRepository.setFeed(listOf(TestData.feedArticle(id = 1, isBookmarked = false)))

        viewModel(articleId = 1).uiState.test {
            val content = awaitItem() as DetailUiState.Content
            assertFalse(content.article.isBookmarked)
        }
    }

    @Test
    fun `onToggleBookmark bookmarks an unbookmarked article`() = runTest {
        articleRepository.setFeed(listOf(TestData.feedArticle(id = 1)))
        val vm = viewModel(articleId = 1)

        vm.uiState.test {
            awaitItem()
            vm.onToggleBookmark()
            assertTrue((awaitItem() as DetailUiState.Content).article.isBookmarked)
        }
    }

    @Test
    fun `onToggleBookmark removes an existing bookmark`() = runTest {
        val article = TestData.article(id = 1)
        bookmarkRepository.setBookmarked(article, bookmarked = true)
        val vm = viewModel(articleId = 1)

        vm.uiState.test {
            assertTrue((awaitItem() as DetailUiState.Content).article.isBookmarked)
            vm.onToggleBookmark()
            assertEquals(DetailUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `onToggleBookmark before content has loaded is a no-op`() = runTest {
        val vm = viewModel(articleId = 1)
        vm.onToggleBookmark()
        assertEquals(emptyList<SavedArticle>(), bookmarkRepository.observeSaved().first())
    }
}
