package com.waynejiang.linefeed.core.data.network

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpaceflightApiTest {
    private val harness = MockApiTestHarness()
    private lateinit var api: SpaceflightApi

    @Before
    fun setUp() {
        harness.start()
        api = harness.api()
    }

    @After
    fun tearDown() {
        harness.shutdown()
    }

    @Test
    fun `parses a real fixture including microsecond updated_at`() = runTest {
        harness.enqueueJsonFromResource("fixtures/spaceflight_articles_page1.json")

        val response = api.getArticles(limit = 5)

        assertTrue(response.results.isNotEmpty())
        val first = response.results.first()
        assertEquals(40010L, first.id)
        assertEquals("2026-09-18T22:10:42.267209Z", first.updatedAt)
    }

    @Test
    fun `parses edge cases - null image, empty summary, unknown fields, missing updated_at`() = runTest {
        harness.enqueueJsonFromResource("fixtures/spaceflight_articles_edge_cases.json")

        val response = api.getArticles(limit = 5)

        assertEquals(2, response.results.size)
        val noImage = response.results.first { it.id == 99001L }
        assertNull(noImage.imageUrl)
        assertEquals("", noImage.summary)

        val noUpdatedAt = response.results.first { it.id == 99002L }
        assertNull(noUpdatedAt.updatedAt)
    }

    @Test
    fun `first page request omits published_at_lte`() = runTest {
        harness.enqueueJsonFromResource("fixtures/spaceflight_articles_page1.json")

        api.getArticles(limit = 20)

        val request = harness.server.takeRequest()
        assertEquals("/v4/articles/?limit=20&ordering=-published_at", request.target)
    }

    @Test
    fun `next page request carries the cursor and ordering`() = runTest {
        harness.enqueueJsonFromResource("fixtures/spaceflight_articles_page1.json")

        api.getArticles(limit = 20, publishedAtLte = "2026-09-21T00:00:00Z")

        val request = harness.server.takeRequest()
        assertEquals(
            "/v4/articles/?limit=20&ordering=-published_at&published_at_lte=2026-09-21T00%3A00%3A00Z",
            request.target,
        )
    }
}
