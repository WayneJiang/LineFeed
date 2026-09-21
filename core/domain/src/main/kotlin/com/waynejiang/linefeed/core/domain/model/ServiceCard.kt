package com.waynejiang.linefeed.core.domain.model

/** A promo/service card sourced from DummyJSON products, mapped into feed-agnostic content. */
data class ServiceCard(
    val id: Long,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val ctaLabel: String,
    val actionUrl: String,
)
