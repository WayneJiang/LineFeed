package com.waynejiang.linefeed.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleWithBookmark
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity
import com.waynejiang.linefeed.core.data.mapper.toAppError
import com.waynejiang.linefeed.core.data.mapper.toEntity
import com.waynejiang.linefeed.core.data.network.ArticleRemoteDataSource
import com.waynejiang.linefeed.core.domain.freshness.FreshnessPolicy
import com.waynejiang.linefeed.core.domain.freshness.RefreshDecision
import com.waynejiang.linefeed.core.domain.freshness.RefreshTrigger
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.time.AppClock
import com.waynejiang.linefeed.core.domain.util.suspendRunCatching
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The only writer of the article cache. `initialize()` reuses the same [FreshnessPolicy] every
 * other source uses so a cold start with a still-fresh cache does not hit the network at all
 * (PLAN.md §3.4); REFRESH always clears and rebuilds `sortIndex` from zero (no merge — see
 * PLAN.md §11 "REFRESH 時 sortIndex 重算"); APPEND walks the keyset cursor stored in
 * `remote_keys`, deduplicating against ids already cached so a page with no genuinely new articles
 * (e.g. all published in the same second as the cursor) terminates pagination instead of looping.
 */
@OptIn(ExperimentalPagingApi::class)
internal class ArticleRemoteMediator @Inject constructor(
    private val database: LineFeedDatabase,
    private val remoteDataSource: ArticleRemoteDataSource,
    private val clock: AppClock,
    private val freshnessPolicy: FreshnessPolicy,
    private val networkMonitor: NetworkMonitor,
) : RemoteMediator<Int, FeedArticleWithBookmark>() {

    override suspend fun initialize(): InitializeAction {
        val lastSuccessAt = database.syncMetadataDao().get(ContentSource.ARTICLES.name)
            ?.lastSuccessAtMillis
            ?.let(Instant::ofEpochMilli)
        val network = networkMonitor.status.first()
        val decision = freshnessPolicy.evaluate(
            source = ContentSource.ARTICLES,
            lastSuccessAt = lastSuccessAt,
            network = network,
            trigger = RefreshTrigger.COLD_START,
        )
        return if (decision is RefreshDecision.Fetch) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, FeedArticleWithBookmark>,
    ): MediatorResult = suspendRunCatching {
        when (loadType) {
            LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
            LoadType.REFRESH -> refresh(state)
            LoadType.APPEND -> append(state)
        }
    }.getOrElse { error ->
        database.syncMetadataDao().markFailure(
            ContentSource.ARTICLES.name,
            clock.now().toEpochMilli(),
            error.toAppError().name,
        )
        MediatorResult.Error(error)
    }

    private suspend fun refresh(state: PagingState<Int, FeedArticleWithBookmark>): MediatorResult {
        val pageSize = state.config.pageSize
        val fetched = remoteDataSource.fetchPage(limit = pageSize, publishedAtLte = null)
        val now = clock.now()
        database.withTransaction {
            val dao = database.feedArticleDao()
            dao.clearAll()
            dao.insertAll(fetched.mapIndexed { index, dto -> dto.toEntity(sortIndex = index.toLong(), fetchedAt = now) })
            database.remoteKeyDao().upsert(
                RemoteKeyEntity(
                    feed = RemoteKeyEntity.ARTICLES_FEED,
                    nextCursorPublishedAtMillis = fetched.lastOrNull()?.let { Instant.parse(it.publishedAt).toEpochMilli() },
                    endOfPaginationReached = fetched.isEmpty(),
                    updatedAtMillis = now.toEpochMilli(),
                ),
            )
            database.syncMetadataDao().markSuccess(ContentSource.ARTICLES.name, now.toEpochMilli())
        }
        return MediatorResult.Success(endOfPaginationReached = fetched.isEmpty())
    }

    private suspend fun append(state: PagingState<Int, FeedArticleWithBookmark>): MediatorResult {
        val remoteKey = database.remoteKeyDao().get(RemoteKeyEntity.ARTICLES_FEED)
            // No successful REFRESH yet (e.g. cold offline start skipped initial refresh): don't
            // guess a cursor and don't hit the network; a later article-refresh request will REFRESH.
            ?: return MediatorResult.Success(endOfPaginationReached = false)
        if (remoteKey.endOfPaginationReached) return MediatorResult.Success(endOfPaginationReached = true)

        val cursor = remoteKey.nextCursorPublishedAtMillis?.let(Instant::ofEpochMilli)
        val fetched = remoteDataSource.fetchPage(limit = state.config.pageSize, publishedAtLte = cursor)
        if (fetched.isEmpty()) {
            database.remoteKeyDao().upsert(remoteKey.copy(endOfPaginationReached = true, updatedAtMillis = clock.now().toEpochMilli()))
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        val now = clock.now()
        val newCount = database.withTransaction {
            val dao = database.feedArticleDao()
            val existing = dao.existingIds(fetched.map { it.id }).toSet()
            val newOnes = fetched.filterNot { it.id in existing }
            if (newOnes.isNotEmpty()) {
                val startIndex = (dao.maxSortIndex() ?: -1L) + 1
                dao.insertAll(newOnes.mapIndexed { i, dto -> dto.toEntity(sortIndex = startIndex + i, fetchedAt = now) })
            }
            database.remoteKeyDao().upsert(
                remoteKey.copy(
                    nextCursorPublishedAtMillis = Instant.parse(fetched.last().publishedAt).toEpochMilli(),
                    endOfPaginationReached = newOnes.isEmpty(),
                    updatedAtMillis = now.toEpochMilli(),
                ),
            )
            newOnes.size
        }
        return MediatorResult.Success(endOfPaginationReached = newCount == 0)
    }
}
