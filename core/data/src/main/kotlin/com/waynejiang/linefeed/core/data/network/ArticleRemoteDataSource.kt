package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.data.network.dto.ArticleDto
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Hides Retrofit/DTO details from the repository layer so it can be faked in tests without a server. */
internal interface ArticleRemoteDataSource {
    suspend fun fetchPage(limit: Int, publishedAtLte: Instant?): List<ArticleDto>
}

internal class RetrofitArticleRemoteDataSource @Inject constructor(
    private val api: SpaceflightApi,
) : ArticleRemoteDataSource {
    override suspend fun fetchPage(limit: Int, publishedAtLte: Instant?): List<ArticleDto> {
        val response = api.getArticles(
            limit = limit,
            publishedAtLte = publishedAtLte?.let(DateTimeFormatter.ISO_INSTANT::format),
        )
        return response.results
    }
}
