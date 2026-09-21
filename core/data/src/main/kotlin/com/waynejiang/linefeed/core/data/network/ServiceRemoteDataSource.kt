package com.waynejiang.linefeed.core.data.network

import com.waynejiang.linefeed.core.data.network.dto.ProductDto
import javax.inject.Inject

internal interface ServiceRemoteDataSource {
    suspend fun fetchProducts(limit: Int): List<ProductDto>
}

internal class RetrofitServiceRemoteDataSource @Inject constructor(
    private val api: DummyJsonApi,
) : ServiceRemoteDataSource {
    override suspend fun fetchProducts(limit: Int): List<ProductDto> = api.getProducts(limit = limit).products
}
