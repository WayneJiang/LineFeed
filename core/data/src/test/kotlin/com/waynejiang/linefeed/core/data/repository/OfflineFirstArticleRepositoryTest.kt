package com.waynejiang.linefeed.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.database.entity.BookmarkEntity
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleEntity
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity
import com.waynejiang.linefeed.core.data.network.ArticleRemoteDataSource
import com.waynejiang.linefeed.core.data.network.dto.ArticleDto
import com.waynejiang.linefeed.core.data.paging.ArticleRemoteMediator
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.testing.FakeClock
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineFirstArticleRepositoryTest {
    private lateinit var database: LineFeedDatabase
    private val clock = FakeClock()

    private val noOpSource = object : ArticleRemoteDataSource {
        override suspend fun fetchPage(limit: Int, publishedAtLte: Instant?): List<ArticleDto> = emptyList()
    }

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun article(id: Long, sortIndex: Long) = FeedArticleEntity(
        id = id,
        sortIndex = sortIndex,
        title = "Article $id",
        summary = "Summary $id",
        newsSite = "Site",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAtMillis = sortIndex,
        updatedAtMillis = null,
        authors = "",
        featured = false,
        fetchedAtMillis = 0L,
    )

    /** A cache already fresh enough that the mediator's `initialize()` skips the network entirely. */
    private suspend fun seedFreshCache(vararg entities: FeedArticleEntity) {
        database.feedArticleDao().insertAll(entities.toList())
        database.remoteKeyDao().upsert(
            RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, null, endOfPaginationReached = true, updatedAtMillis = clock.now().toEpochMilli()),
        )
        database.syncMetadataDao().markSuccess(ContentSource.ARTICLES.name, clock.now().toEpochMilli())
    }

    private fun repository() = OfflineFirstArticleRepository(
        database = database,
        remoteMediator = ArticleRemoteMediator(
            database = database,
            remoteDataSource = noOpSource,
            clock = clock,
            freshnessPolicy = FreshnessPolicy(clock),
            networkMonitor = FakeNetworkMonitor(),
        ),
    )

    @Test
    fun `feedPagingData returns cached articles ordered by sortIndex`() = runBlocking {
        seedFreshCache(article(id = 2, sortIndex = 1), article(id = 1, sortIndex = 0))

        val snapshot = repository().feedPagingData().asSnapshot()

        assertEquals(listOf(1L, 2L), snapshot.map { it.article.id })
        assertEquals(listOf(0L, 1L), snapshot.map { it.sortIndex })
    }

    @Test
    fun `feedPagingData reflects bookmark state`() = runBlocking {
        seedFreshCache(article(id = 1, sortIndex = 0))
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

        val snapshot = repository().feedPagingData().asSnapshot()

        assertTrue(snapshot.single().article.isBookmarked)
    }

    @Test
    fun `observeArticle emits null then the article once cached`() = runBlocking {
        val repo = repository()

        repo.observeArticle(1).test {
            assertNull(awaitItem())
            database.feedArticleDao().insertAll(listOf(article(id = 1, sortIndex = 0)))
            assertEquals(1L, awaitItem()?.id)
        }
    }
}
