package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.data.network.dto.ProductDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceCardMappersTest {
    private fun product(id: Long = 1, discountPercentage: Double = 10.48) = ProductDto(
        id = id,
        title = "Product $id",
        description = "Description $id",
        price = 9.99,
        discountPercentage = discountPercentage,
        rating = 4.5,
        thumbnail = "https://example.com/$id.webp",
        category = "beauty",
        brand = null,
    )

    @Test
    fun `ctaLabel rounds the discount percentage`() {
        val entity = product(discountPercentage = 10.48).toEntity(position = 0)
        assertEquals("Get 10% off", entity.ctaLabel)
    }

    @Test
    fun `discount under 1 percent uses Open instead of a rounded 0 percent`() {
        val entity = product(discountPercentage = 0.4).toEntity(position = 0)
        assertEquals("Open", entity.ctaLabel)
    }

    @Test
    fun `actionUrl points at the dummyjson product page`() {
        val entity = product(id = 42).toEntity(position = 0)
        assertEquals("https://dummyjson.com/products/42", entity.actionUrl)
    }

    @Test
    fun `entity to domain preserves the mapped fields`() {
        val entity = product(id = 7).toEntity(position = 3)
        val domain = entity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.ctaLabel, domain.ctaLabel)
        assertEquals(entity.actionUrl, domain.actionUrl)
    }
}
