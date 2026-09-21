package com.waynejiang.linefeed.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.testing.FakeClock
import com.waynejiang.linefeed.core.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Snapshot behavior only, as noted in PLAN.md §10 step 7 ("先不含圖片下載"); image download
 * coverage is added to this class in step 12.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DefaultBookmarkRepositoryTest {
    private lateinit var database: LineFeedDatabase
    private lateinit var repository: DefaultBookmarkRepository
    private val clock = FakeClock()

    @Before
    fun setUp() {
        database = createTestDatabase()
        repository = DefaultBookmarkRepository(bookmarkDao = database.bookmarkDao(), clock = clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `setBookmarked true stores a snapshot, false removes it`() = runBlocking {
        val article = TestData.article(id = 1, title = "Rocket launch")

        repository.setBookmarked(article, bookmarked = true)
        assertEquals(setOf(1L), repository.observeBookmarkedIds().first())
        assertEquals("Rocket launch", repository.observeSaved().first().single().article.title)

        repository.setBookmarked(article, bookmarked = false)
        assertTrue(repository.observeSaved().first().isEmpty())
    }

    @Test
    fun `savedAt uses the clock at the time of bookmarking`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1), bookmarked = true)
        assertEquals(clock.now(), repository.observeSaved().first().single().savedAt)
    }

    @Test
    fun `observeSaved filters by query against title and news site, case-insensitively`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, title = "Rocket launch", newsSite = "SpaceNews"), bookmarked = true)
        repository.setBookmarked(TestData.article(id = 2, title = "Satellite deployed", newsSite = "OrbitDaily"), bookmarked = true)

        assertEquals(listOf(1L), repository.observeSaved(query = "rocket").first().map { it.article.id })
        assertEquals(listOf(2L), repository.observeSaved(query = "orbitdaily").first().map { it.article.id })
        assertEquals(2, repository.observeSaved(query = "").first().size)
    }

    @Test
    fun `observeSaved query with LIKE wildcard characters is treated literally`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, title = "100% off"), bookmarked = true)
        repository.setBookmarked(TestData.article(id = 2, title = "anything else"), bookmarked = true)

        assertEquals(listOf(1L), repository.observeSaved(query = "100%").first().map { it.article.id })
    }

    @Test
    fun `retryPendingImageDownloads is a no-op for now`() = runBlocking {
        repository.retryPendingImageDownloads()
    }
}
