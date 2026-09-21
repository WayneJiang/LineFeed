package com.waynejiang.linefeed.core.domain.repository

import androidx.paging.PagingData
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import com.waynejiang.linefeed.core.domain.model.ServiceCard
import com.waynejiang.linefeed.core.domain.model.Weather
import kotlinx.coroutines.flow.Flow

/**
 * The paged article feed. Backed by Room's `PagingSource` + an `ArticleRemoteMediator` in
 * `core:data` — that mediator is the *only* code that writes to the article cache, and it is the
 * only thing that decides when to fetch the next page (keyset cursor) or a fresh first page
 * (freshness policy on `initialize()`). This interface therefore has no `refresh()`/`loadNextPage()`
 * of its own; scrolling and pull-to-refresh drive Paging directly from the UI layer.
 */
interface ArticleRepository {
    fun feedPagingData(): Flow<PagingData<FeedArticle>>
    fun observeArticle(id: Long): Flow<Article?>
}

interface BookmarkRepository {
    fun observeSaved(query: String = ""): Flow<List<SavedArticle>>
    fun observeBookmarkedIds(): Flow<Set<Long>>
    suspend fun setBookmarked(article: Article, bookmarked: Boolean)
    suspend fun retryPendingImageDownloads()
}

interface WeatherRepository {
    fun observeWeather(): Flow<Weather?>
}

interface ServiceCardRepository {
    fun observeServiceCards(): Flow<List<ServiceCard>>
}
