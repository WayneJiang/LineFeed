package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.data.network.dto.ArticleListResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface SpaceflightApi {
    @GET("v4/articles/")
    suspend fun getArticles(
        @Query("limit") limit: Int,
        @Query("ordering") ordering: String = "-published_at",
        @Query("published_at_lte") publishedAtLte: String? = null,
    ): ArticleListResponseDto
}
