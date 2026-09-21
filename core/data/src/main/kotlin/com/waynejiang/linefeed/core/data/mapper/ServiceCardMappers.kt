package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.data.database.entity.ServiceCardEntity
import com.waynejiang.linefeed.core.data.network.dto.ProductDto
import com.waynejiang.linefeed.core.domain.model.ServiceCard
import kotlin.math.roundToInt

private fun ctaLabelFor(discountPercentage: Double): String =
    if (discountPercentage < 1.0) "Open" else "Get ${discountPercentage.roundToInt()}% off"

fun ProductDto.toEntity(position: Int): ServiceCardEntity = ServiceCardEntity(
    id = id,
    title = title,
    description = description,
    imageUrl = thumbnail,
    ctaLabel = ctaLabelFor(discountPercentage),
    actionUrl = "https://dummyjson.com/products/$id",
    position = position,
)

fun ServiceCardEntity.toDomain(): ServiceCard = ServiceCard(
    id = id,
    title = title,
    description = description,
    imageUrl = imageUrl,
    ctaLabel = ctaLabel,
    actionUrl = actionUrl,
)
