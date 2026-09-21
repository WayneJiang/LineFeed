package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.data.database.entity.BookmarkEntity
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleEntity
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleEntity.Companion.AUTHORS_SEPARATOR
import com.waynejiang.linefeed.core.data.database.entity.FeedArticleWithBookmark
import com.waynejiang.linefeed.core.data.network.dto.ArticleDto
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.FeedArticle
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import java.time.Instant

/** Blank/empty is treated the same as absent: the API returns `""` for `image_url` at least as often as omitting it. */
private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun joinAuthors(authors: List<String>): String = authors.joinToString(AUTHORS_SEPARATOR)

private fun splitAuthors(joined: String): List<String> =
    if (joined.isEmpty()) emptyList() else joined.split(AUTHORS_SEPARATOR)

fun ArticleDto.toEntity(sortIndex: Long, fetchedAt: Instant): FeedArticleEntity = FeedArticleEntity(
    id = id,
    sortIndex = sortIndex,
    title = title,
    summary = summary,
    newsSite = newsSite,
    url = url,
    imageUrl = imageUrl.orNullIfBlank(),
    publishedAtMillis = Instant.parse(publishedAt).toEpochMilli(),
    updatedAtMillis = updatedAt?.let(Instant::parse)?.toEpochMilli(),
    authors = joinAuthors(authors.map { it.name }),
    featured = featured,
    fetchedAtMillis = fetchedAt.toEpochMilli(),
)

fun FeedArticleEntity.toDomain(isBookmarked: Boolean): Article = Article(
    id = id,
    title = title,
    summary = summary,
    newsSite = newsSite,
    url = url,
    imageUrl = imageUrl,
    publishedAt = Instant.ofEpochMilli(publishedAtMillis),
    authors = splitAuthors(authors),
    isBookmarked = isBookmarked,
)

fun FeedArticleWithBookmark.toDomain(): FeedArticle = FeedArticle(
    article = article.toDomain(isBookmarked = isBookmarked),
    sortIndex = article.sortIndex,
)

fun BookmarkEntity.toDomain(): SavedArticle = SavedArticle(
    article = Article(
        id = articleId,
        title = title,
        summary = summary,
        newsSite = newsSite,
        url = url,
        imageUrl = imageUrl,
        publishedAt = Instant.ofEpochMilli(publishedAtMillis),
        authors = splitAuthors(authors),
        isBookmarked = true,
    ),
    savedAt = Instant.ofEpochMilli(savedAtMillis),
    localImagePath = localImagePath,
)

fun Article.toBookmarkEntity(savedAt: Instant): BookmarkEntity = BookmarkEntity(
    articleId = id,
    title = title,
    summary = summary,
    newsSite = newsSite,
    url = url,
    imageUrl = imageUrl,
    localImagePath = null,
    publishedAtMillis = publishedAt.toEpochMilli(),
    authors = joinAuthors(authors),
    savedAtMillis = savedAt.toEpochMilli(),
)
