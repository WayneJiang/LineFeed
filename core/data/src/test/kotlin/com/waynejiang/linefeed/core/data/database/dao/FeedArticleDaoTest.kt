package com.waynejiang.linefeed.core.data.database.dao

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.database.entity.BookmarkEntity
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FeedArticleDaoTest {
    private lateinit var database: LineFeedDatabase
    private lateinit var dao: FeedArticleDao

    @Before
    fun setUp() {
        database = createTestDatabase()
        dao = database.feedArticleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun article(id: Long, sortIndex: Long, publishedAtMillis: Long = sortIndex) = FeedArticleEntity(
        id = id,
        sortIndex = sortIndex,
        title = "Article $id",
        summary = "Summary $id",
        newsSite = "Site",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAtMillis = publishedAtMillis,
        updatedAtMillis = null,
        authors = "",
        featured = false,
        fetchedAtMillis = 0L,
    )

    @Test
    fun `pagingSource orders by sortIndex ascending and joins bookmark state`() = runBlocking {
        dao.insertAll(listOf(article(id = 3, sortIndex = 2), article(id = 1, sortIndex = 0), article(id = 2, sortIndex = 1)))
        database.bookmarkDao().upsert(
            BookmarkEntity(
                articleId = 2,
                title = "t",
                summary = "s",
                newsSite = "n",
                url = "u",
                imageUrl = null,
                localImagePath = null,
                publishedAtMillis = 0,
                authors = "",
                savedAtMillis = 0,
            ),
        )

        val pager = TestPager(PagingConfig(pageSize = 10), dao.pagingSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(listOf(1L, 2L, 3L), result.data.map { it.article.id })
        assertFalse(result.data.first { it.article.id == 1L }.isBookmarked)
        assertTrue(result.data.first { it.article.id == 2L }.isBookmarked)
        assertFalse(result.data.first { it.article.id == 3L }.isBookmarked)
    }

    @Test
    fun `TestPager append walks subsequent pages in sortIndex order`() = runBlocking {
        dao.insertAll((0 until 5).map { article(id = it.toLong(), sortIndex = it.toLong()) })

        val pager = TestPager(PagingConfig(pageSize = 2, enablePlaceholders = false), dao.pagingSource())
        pager.refresh()
        pager.append()
        val pages = pager.getPages()

        val ids = pages.flatMap { page -> page.data.map { row -> row.article.id } }
        // pageSize=2 with the default prefetchDistance means a single append can pull in more than
        // one page's worth of items; what matters here is contiguous, ascending sortIndex order
        // with no gaps or duplicates, not an exact item count.
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.size >= 4)
    }

    @Test
    fun `bookmarking an article invalidates the paging source`() = runBlocking {
        dao.insertAll(listOf(article(id = 1, sortIndex = 0)))
        val pagingSource = dao.pagingSource()
        val pager = TestPager(PagingConfig(pageSize = 10), pagingSource)
        pager.refresh()
        assertFalse(pagingSource.invalid)

        database.bookmarkDao().upsert(
            BookmarkEntity(
                articleId = 1,
                title = "t",
                summary = "s",
                newsSite = "n",
                url = "u",
                imageUrl = null,
                localImagePath = null,
                publishedAtMillis = 0,
                authors = "",
                savedAtMillis = 0,
            ),
        )

        val becameInvalid = awaitTrue(timeoutMillis = 3000) { pagingSource.invalid }
        assertTrue("expected pagingSource to become invalid after a bookmark change", becameInvalid)
    }

    @Test
    fun `insertAll ignores conflicts so an existing sortIndex is not overwritten`() = runBlocking {
        dao.insertAll(listOf(article(id = 1, sortIndex = 0)))
        dao.insertAll(listOf(article(id = 1, sortIndex = 99)))

        val stored = dao.findById(1)
        assertEquals(0L, stored?.sortIndex)
    }

    @Test
    fun `maxSortIndex, existingIds and clearAll`() = runBlocking {
        assertNull(dao.maxSortIndex())

        dao.insertAll((0 until 3).map { article(id = it.toLong(), sortIndex = it.toLong()) })

        assertEquals(2L, dao.maxSortIndex())
        assertEquals(listOf(1L, 2L), dao.existingIds(listOf(1L, 2L, 99L)).sorted())
        assertEquals(3, dao.count())

        dao.clearAll()
        assertEquals(0, dao.count())
        assertNull(dao.maxSortIndex())
    }

    private suspend fun awaitTrue(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            kotlinx.coroutines.delay(20)
        }
        return condition()
    }
}
