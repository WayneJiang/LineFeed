package com.waynejiang.linefeed.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.network.ImageDownloader
import com.waynejiang.linefeed.core.testing.FakeClock
import com.waynejiang.linefeed.core.testing.TestData
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DefaultBookmarkRepositoryTest {
    private lateinit var database: LineFeedDatabase
    private val clock = FakeClock()
    // Unconfined: `scope.launch { downloadImage(...) }` in DefaultBookmarkRepository runs eagerly,
    // up to its first real suspension point, so a FakeImageDownloader with no gate completes
    // synchronously and assertions right after setBookmarked() don't need advanceUntilIdle().
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private class FakeImageDownloader : ImageDownloader {
        var succeed = true
        var gate: CompletableDeferred<Unit>? = null
        val attemptedUrls = mutableListOf<String>()

        override suspend fun download(url: String, destination: File): Boolean {
            attemptedUrls += url
            gate?.await()
            if (!succeed) return false
            destination.parentFile?.mkdirs()
            destination.writeText("fake-image-bytes")
            return true
        }
    }

    private lateinit var imageDownloader: FakeImageDownloader
    private lateinit var repository: DefaultBookmarkRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        imageDownloader = FakeImageDownloader()
        repository = DefaultBookmarkRepository(
            bookmarkDao = database.bookmarkDao(),
            clock = clock,
            context = ApplicationProvider.getApplicationContext<Context>(),
            imageDownloader = imageDownloader,
            scope = scope,
        )
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
    fun `successful download sets localImagePath to a real file`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, imageUrl = "https://example.com/1.jpg"), bookmarked = true)

        val saved = repository.observeSavedArticle(1).first()
        assertTrue(saved?.localImagePath != null)
        assertTrue(File(saved!!.localImagePath!!).exists())
    }

    @Test
    fun `failed download leaves localImagePath null`() = runBlocking {
        imageDownloader.succeed = false

        repository.setBookmarked(TestData.article(id = 1, imageUrl = "https://example.com/1.jpg"), bookmarked = true)

        assertNull(repository.observeSavedArticle(1).first()?.localImagePath)
    }

    @Test
    fun `an article with no image url never attempts a download`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, imageUrl = null), bookmarked = true)

        assertTrue(imageDownloader.attemptedUrls.isEmpty())
        assertNull(repository.observeSavedArticle(1).first()?.localImagePath)
    }

    @Test
    fun `unbookmarking deletes the previously downloaded image file`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, imageUrl = "https://example.com/1.jpg"), bookmarked = true)
        val file = File(repository.observeSavedArticle(1).first()!!.localImagePath!!)
        assertTrue(file.exists())

        repository.setBookmarked(TestData.article(id = 1), bookmarked = false)

        assertFalse(file.exists())
    }

    @Test
    fun `unbookmarking while a download is still in flight cancels it instead of racing it`() = runBlocking {
        imageDownloader.gate = CompletableDeferred()
        val article = TestData.article(id = 1, imageUrl = "https://example.com/1.jpg")

        repository.setBookmarked(article, bookmarked = true)
        // The download is parked on the gate right now; un-bookmark before it can finish.
        repository.setBookmarked(article, bookmarked = false)
        imageDownloader.gate?.complete(Unit)

        assertTrue(repository.observeSaved().first().isEmpty())
    }

    @Test
    fun `retryPendingImageDownloads downloads every bookmark missing a local image`() = runBlocking {
        imageDownloader.succeed = false
        repository.setBookmarked(TestData.article(id = 1, imageUrl = "https://example.com/1.jpg"), bookmarked = true)
        assertNull(repository.observeSavedArticle(1).first()?.localImagePath)

        imageDownloader.succeed = true
        repository.retryPendingImageDownloads()

        assertTrue(repository.observeSavedArticle(1).first()?.localImagePath != null)
    }

    @Test
    fun `retryPendingImageDownloads does nothing when every bookmark already has a local image`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, imageUrl = "https://example.com/1.jpg"), bookmarked = true)
        imageDownloader.attemptedUrls.clear()

        repository.retryPendingImageDownloads()

        assertTrue(imageDownloader.attemptedUrls.isEmpty())
    }
}
