package com.waynejiang.linefeed.core.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArticleListResponseDto(
    val count: Int = 0,
    val next: String? = null,
    val results: List<ArticleDto> = emptyList(),
)

/**
 * Spaceflight News API v4 article. Every optional/nullable default here reflects a real quirk
 * observed against the live API (PLAN.md §6.1): `image_url` can be null or an empty string,
 * `summary` can be empty, `updated_at` includes microseconds and can be absent.
 */
@Serializable
data class ArticleDto(
    val id: Long,
    val title: String,
    val authors: List<AuthorDto> = emptyList(),
    val url: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("news_site") val newsSite: String = "",
    val summary: String = "",
    @SerialName("published_at") val publishedAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val featured: Boolean = false,
)

@Serializable
data class AuthorDto(val name: String)
