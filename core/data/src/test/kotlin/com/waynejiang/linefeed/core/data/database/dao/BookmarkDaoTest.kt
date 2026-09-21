package com.waynejiang.linefeed.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.database.entity.BookmarkEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BookmarkDaoTest {
    private lateinit var database: LineFeedDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setUp() {
        database = createTestDatabase()
        dao = database.bookmarkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun bookmark(
        id: Long,
        title: String = "Article $id",
        newsSite: String = "Site $id",
        imageUrl: String? = "https://example.com/$id.jpg",
        localImagePath: String? = null,
        savedAtMillis: Long = id,
    ) = BookmarkEntity(
        articleId = id,
        title = title,
        summary = "summary",
        newsSite = newsSite,
        url = "https://example.com/$id",
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        publishedAtMillis = 0,
        authors = "",
        savedAtMillis = savedAtMillis,
    )

    @Test
    fun `search is case-insensitive and matches either title or news site`() = runTest {
        dao.upsert(bookmark(id = 1, title = "Falcon Heavy launches", newsSite = "SpaceNews"))
        dao.upsert(bookmark(id = 2, title = "Something else entirely", newsSite = "Falcon Times"))
        dao.upsert(bookmark(id = 3, title = "Unrelated", newsSite = "Other"))

        dao.observeAll("falcon").test {
            val ids = awaitItem().map { it.articleId }.sorted()
            assertEquals(listOf(1L, 2L), ids)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `percent and underscore wildcards in the query are escaped by the caller`() = runTest {
        dao.upsert(bookmark(id = 1, title = "100% off sale"))
        dao.upsert(bookmark(id = 2, title = "unrelated"))

        // The DAO trusts the caller to escape LIKE wildcards before calling it; a raw "%" in the
        // query must be passed as "\%" or it would match everything.
        val escapedQuery = "100\\% off"
        dao.observeAll(escapedQuery).test {
            val ids = awaitItem().map { it.articleId }
            assertEquals(listOf(1L), ids)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `pendingImageDownloads returns bookmarks with an imageUrl but no local path`() = runTest {
        dao.upsert(bookmark(id = 1, imageUrl = "https://example.com/1.jpg", localImagePath = null))
        dao.upsert(bookmark(id = 2, imageUrl = "https://example.com/2.jpg", localImagePath = "/files/2.jpg"))
        dao.upsert(bookmark(id = 3, imageUrl = null, localImagePath = null))

        val pending = dao.pendingImageDownloads()

        assertEquals(listOf(1L), pending.map { it.articleId })
    }

    @Test
    fun `updateLocalImagePath and delete`() = runTest {
        dao.upsert(bookmark(id = 1))
        dao.updateLocalImagePath(1, "/files/1.jpg")

        dao.observeById(1).test {
            assertEquals("/files/1.jpg", awaitItem()?.localImagePath)
            cancelAndConsumeRemainingEvents()
        }

        dao.delete(1)
        dao.observeIds().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }
}
