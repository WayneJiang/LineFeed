package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.data.network.dto.ProductListResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface DummyJsonApi {
    @GET("products")
    suspend fun getProducts(
        @Query("limit") limit: Int,
        @Query("select") select: String = SELECT_FIELDS,
    ): ProductListResponseDto

    companion object {
        // Only request the fields the service-card mapping actually uses: cuts payload size
        // roughly 60% versus the unfiltered response (PLAN.md §3.1).
        const val SELECT_FIELDS = "title,description,price,discountPercentage,rating,thumbnail,category,brand"
    }
}
