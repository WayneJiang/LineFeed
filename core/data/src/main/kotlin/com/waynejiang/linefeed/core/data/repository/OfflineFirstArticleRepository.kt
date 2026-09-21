package com.waynejiang.linefeed.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.mapper.toDomain
import com.waynejiang.linefeed.core.data.paging.ArticleRemoteMediator
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.repository.ArticleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val PAGE_SIZE = 20

/**
 * Room + [ArticleRemoteMediator] wired into a single [Pager]. All paging policy (page size,
 * prefetch distance, no placeholders — bookmark joins make positional placeholders meaningless)
 * lives here, not in the ViewModel, so `feature:feed` only ever deals in domain types.
 */
@OptIn(ExperimentalPagingApi::class)
class OfflineFirstArticleRepository @Inject internal constructor(
    private val database: LineFeedDatabase,
    private val remoteMediator: ArticleRemoteMediator,
) : ArticleRepository {

    override fun feedPagingData(): Flow<PagingData<FeedArticle>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = remoteMediator,
            pagingSourceFactory = { database.feedArticleDao().pagingSource() },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeArticle(id: Long): Flow<Article?> =
        database.feedArticleDao().observeById(id).map { it?.toDomain()?.article }
}
