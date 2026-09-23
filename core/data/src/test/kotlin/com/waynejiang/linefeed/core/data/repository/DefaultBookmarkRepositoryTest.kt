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
import kotlinx.coroutines.withTimeout
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
    // up to its first *real* suspension point. `imageDownloader.download(...)` itself has no
    // suspension when there's no gate, so `attemptedUrls` is always populated synchronously - but
    // the DAO calls right after it (`bookmarkDao.findById`/`updateLocalImagePath`) dispatch onto a
    // genuine background dispatcher (RoomTestDatabase's real Dispatchers.IO query context, not a
    // test dispatcher), so THAT part keeps running on a background thread after setBookmarked()
    // already returned. Tests that only check `attemptedUrls` right after setBookmarked() are fine;
    // tests that check `localImagePath` (or that a later query no longer sees a pending download)
    // must wait for that background write via awaitLocalImagePath() instead of asserting immediately.
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

    /**
     * `setBookmarked(bookmarked = true)` returns as soon as the background download job hits its
     * first real (non-test-dispatcher) suspension point - it does not wait for the job to finish
     * writing `localImagePath`. Poll the saved article instead of reading it once, so this waits for
     * that write instead of racing it.
     */
    private suspend fun awaitLocalImagePath(articleId: Long): String = withTimeout(3_000) {
        repository.observeSavedArticle(articleId).first { it?.localImagePath != null }!!.localImagePath!!
    }

    @Test
    fun `successful download sets localImagePath to a real file`() = runBlocking {
        repository.setBookmarked(TestData.article(id = 1, imageUrl = "https://example.com/1.jpg"), bookmarked = true)

        assertTrue(File(awaitLocalImagePath(1)).exists())
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
        val file = File(awaitLocalImagePath(1))
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
        // Wait for the initial download's DB write to land before clearing: otherwise
        // retryPendingImageDownloads() below can still see `localImagePath IS NULL` (the write is
        // still in flight on a background dispatcher) and treat this bookmark as pending, downloading
        // it again and re-populating attemptedUrls.
        awaitLocalImagePath(1)
        imageDownloader.attemptedUrls.clear()

        repository.retryPendingImageDownloads()

        assertTrue(imageDownloader.attemptedUrls.isEmpty())
    }
}
