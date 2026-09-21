package com.waynejiang.linefeed.core.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductListResponseDto(
    val products: List<ProductDto> = emptyList(),
)

/** DummyJSON product, requested with `select=` to shrink the payload (PLAN.md §6.3). `brand` can be absent. */
@Serializable
data class ProductDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val price: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val rating: Double = 0.0,
    val thumbnail: String? = null,
    val category: String = "",
    val brand: String? = null,
)
