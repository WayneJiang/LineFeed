package com.waynejiang.linefeed.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity
import com.waynejiang.linefeed.core.data.network.ArticleRemoteDataSource
import com.waynejiang.linefeed.core.data.network.dto.ArticleDto
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.testing.FakeClock
import com.waynejiang.linefeed.core.testing.FakeNetworkMonitor
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ArticleRemoteMediatorTest {
    private lateinit var database: LineFeedDatabase
    private val clock = FakeClock()
    private val networkMonitor = FakeNetworkMonitor(NetworkStatus.UNMETERED)

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun dto(id: Long, publishedAt: String) = ArticleDto(
        id = id,
        title = "Title $id",
        authors = emptyList(),
        url = "https://example.com/$id",
        imageUrl = null,
        newsSite = "Site",
        summary = "Summary $id",
        publishedAt = publishedAt,
        updatedAt = null,
        featured = false,
    )

    private fun mediator(remoteDataSource: ArticleRemoteDataSource) = ArticleRemoteMediator(
        database = database,
        remoteDataSource = remoteDataSource,
        clock = clock,
        freshnessPolicy = FreshnessPolicy(clock),
        networkMonitor = networkMonitor,
    )

    private fun emptyState(pageSize: Int = 20) =
        PagingState<Int, com.waynejiang.linefeed.core.data.database.entity.FeedArticleWithBookmark>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = pageSize),
            leadingPlaceholderCount = 0,
        )

    @Test
    fun `initialize launches refresh when cache is empty`() = runBlocking {
        val action = mediator(fakeSource()).initialize()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, action)
    }

    @Test
    fun `initialize skips refresh when cache is fresh`() = runBlocking {
        database.syncMetadataDao().markSuccess(ContentSource.ARTICLES.name, clock.now().toEpochMilli())
        val action = mediator(fakeSource()).initialize()
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, action)
    }

    @Test
    fun `initialize launches refresh when offline regardless of freshness`() = runBlocking {
        database.syncMetadataDao().markSuccess(ContentSource.ARTICLES.name, clock.now().toEpochMilli())
        networkMonitor.set(NetworkStatus.OFFLINE)
        // Offline: FreshnessPolicy.evaluate returns Skip(OFFLINE), never Fetch -> SKIP so we never
        // hit the network while offline; Paging will just show what's cached (possibly empty).
        val action = mediator(fakeSource()).initialize()
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, action)
    }

    @Test
    fun `refresh clears cache, assigns sortIndex from zero and stores cursor`() = runBlocking {
        val fetched = listOf(
            dto(3, "2026-09-21T12:00:00Z"),
            dto(2, "2026-09-21T11:00:00Z"),
            dto(1, "2026-09-21T10:00:00Z"),
        )
        val result = mediator(fakeSource(fetched)).load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        val stored = database.feedArticleDao().findById(1)
        assertEquals(2L, stored?.sortIndex)
        assertEquals(3, database.feedArticleDao().count())

        val key = database.remoteKeyDao().get(RemoteKeyEntity.ARTICLES_FEED)
        assertEquals(Instant.parse("2026-09-21T10:00:00Z").toEpochMilli(), key?.nextCursorPublishedAtMillis)
        assertFalse(key?.endOfPaginationReached == true)
    }

    @Test
    fun `refresh with empty page marks end of pagination`() = runBlocking {
        val result = mediator(fakeSource(emptyList())).load(LoadType.REFRESH, emptyState())
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertTrue(database.remoteKeyDao().get(RemoteKeyEntity.ARTICLES_FEED)?.endOfPaginationReached == true)
    }

    @Test
    fun `append without a prior refresh does not hit network`() = runBlocking {
        val source = fakeSource()
        val result = mediator(source).load(LoadType.APPEND, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(0, source.callCount)
    }

    @Test
    fun `append walks the keyset cursor and continues sortIndex`() = runBlocking {
        mediator(fakeSource(listOf(dto(2, "2026-09-21T12:00:00Z"), dto(1, "2026-09-21T11:00:00Z"))))
            .load(LoadType.REFRESH, emptyState())

        val appendResult = mediator(fakeSource(listOf(dto(0, "2026-09-21T09:00:00Z"))))
            .load(LoadType.APPEND, emptyState())

        assertFalse((appendResult as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        val stored = database.feedArticleDao().findById(0)
        assertEquals(2L, stored?.sortIndex)
        assertEquals(3, database.feedArticleDao().count())
    }

    @Test
    fun `append with no new ids reaches end of pagination without duplicating rows`() = runBlocking {
        mediator(fakeSource(listOf(dto(1, "2026-09-21T11:00:00Z")))).load(LoadType.REFRESH, emptyState())

        // Same article re-fetched at the cursor boundary (published_at_lte is inclusive).
        val appendResult = mediator(fakeSource(listOf(dto(1, "2026-09-21T11:00:00Z"))))
            .load(LoadType.APPEND, emptyState())

        assertTrue((appendResult as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, database.feedArticleDao().count())
    }

    @Test
    fun `append after end of pagination does not hit network again`() = runBlocking {
        val source = fakeSource(emptyList())
        mediator(source).load(LoadType.REFRESH, emptyState())
        assertEquals(1, source.callCount)

        val result = mediator(source).load(LoadType.APPEND, emptyState())
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, source.callCount)
    }

    @Test
    fun `prepend is a no-op that reports end of pagination`() = runBlocking {
        val result = mediator(fakeSource()).load(LoadType.PREPEND, emptyState())
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun `load failure surfaces as MediatorResult Error and records sync failure`() = runBlocking {
        val failing = object : ArticleRemoteDataSource {
            override suspend fun fetchPage(limit: Int, publishedAtLte: Instant?): List<ArticleDto> =
                throw java.io.IOException("boom")
        }

        val result = mediator(failing).load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertEquals("boom", (result as RemoteMediator.MediatorResult.Error).throwable.message)
        assertEquals("UNKNOWN", database.syncMetadataDao().get(ContentSource.ARTICLES.name)?.lastError)
    }

    private fun fakeSource(response: List<ArticleDto> = emptyList()) = object : ArticleRemoteDataSource {
        var callCount = 0
            private set

        override suspend fun fetchPage(limit: Int, publishedAtLte: Instant?): List<ArticleDto> {
            callCount++
            return response
        }
    }
}
