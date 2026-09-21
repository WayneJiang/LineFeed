package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.data.database.entity.FeedArticleWithBookmark
import com.waynejiang.linefeed.core.data.network.dto.ArticleDto
import com.waynejiang.linefeed.core.data.network.dto.AuthorDto
import com.waynejiang.linefeed.core.domain.model.Article
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleMappersTest {
    private val fetchedAt: Instant = Instant.parse("2026-09-21T00:00:00Z")

    private fun dto(
        id: Long = 1,
        imageUrl: String? = "https://example.com/1.jpg",
        authors: List<AuthorDto> = listOf(AuthorDto("Jane Doe"), AuthorDto("John Roe")),
        updatedAt: String? = "2026-09-18T23:10:20.648543Z",
    ) = ArticleDto(
        id = id,
        title = "Title $id",
        authors = authors,
        url = "https://example.com/$id",
        imageUrl = imageUrl,
        newsSite = "Example",
        summary = "Summary $id",
        publishedAt = "2026-09-21T14:00:00Z",
        updatedAt = updatedAt,
        featured = false,
    )

    @Test
    fun `parses ISO instants including microsecond precision`() {
        val entity = dto().toEntity(sortIndex = 0, fetchedAt = fetchedAt)

        assertEquals(Instant.parse("2026-09-21T14:00:00Z").toEpochMilli(), entity.publishedAtMillis)
        assertEquals(Instant.parse("2026-09-18T23:10:20.648543Z").toEpochMilli(), entity.updatedAtMillis)
    }

    @Test
    fun `blank or null image url becomes null`() {
        assertNull(dto(imageUrl = null).toEntity(0, fetchedAt).imageUrl)
        assertNull(dto(imageUrl = "").toEntity(0, fetchedAt).imageUrl)
        assertEquals("https://example.com/1.jpg", dto(imageUrl = "https://example.com/1.jpg").toEntity(0, fetchedAt).imageUrl)
    }

    @Test
    fun `authors are joined and split back losslessly, including zero authors`() {
        val entity = dto(authors = listOf(AuthorDto("Jane Doe"), AuthorDto("John Roe"))).toEntity(0, fetchedAt)
        val domain = entity.toDomain(isBookmarked = false)
        assertEquals(listOf("Jane Doe", "John Roe"), domain.authors)

        val noAuthors = dto(authors = emptyList()).toEntity(0, fetchedAt).toDomain(isBookmarked = false)
        assertEquals(emptyList<String>(), noAuthors.authors)
    }

    @Test
    fun `entity to domain round trip preserves isBookmarked and sortIndex via FeedArticleWithBookmark`() {
        val entity = dto(id = 42).toEntity(sortIndex = 7, fetchedAt = fetchedAt)
        val withBookmark = FeedArticleWithBookmark(article = entity, isBookmarked = true)

        val feedArticle = withBookmark.toDomain()

        assertEquals(42L, feedArticle.article.id)
        assertEquals(7L, feedArticle.sortIndex)
        assertTrue(feedArticle.article.isBookmarked)
    }

    @Test
    fun `article to bookmark entity and back preserves fields`() {
        val article = Article(
            id = 5,
            title = "Title",
            summary = "Summary",
            newsSite = "Site",
            url = "https://example.com/5",
            imageUrl = "https://example.com/5.jpg",
            publishedAt = Instant.parse("2026-09-20T00:00:00Z"),
            authors = listOf("A", "B"),
            isBookmarked = false,
        )
        val savedAt = Instant.parse("2026-09-21T00:00:00Z")

        val entity = article.toBookmarkEntity(savedAt)
        val savedArticle = entity.toDomain()

        assertEquals(article.id, savedArticle.article.id)
        assertEquals(article.title, savedArticle.article.title)
        assertEquals(article.authors, savedArticle.article.authors)
        assertTrue(savedArticle.article.isBookmarked)
        assertEquals(savedAt, savedArticle.savedAt)
        assertFalse(entity.localImagePath != null)
    }
}
