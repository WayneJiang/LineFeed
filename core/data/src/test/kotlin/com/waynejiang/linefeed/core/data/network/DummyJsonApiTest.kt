package com.waynejiang.linefeed.core.data.network

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DummyJsonApiTest {
    private val harness = MockApiTestHarness()
    private lateinit var api: DummyJsonApi

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
    fun `parses a real products fixture`() = runTest {
        harness.enqueueJsonFromResource("fixtures/dummyjson_products.json")

        val response = api.getProducts(limit = 10)

        assertEquals(10, response.products.size)
        val first = response.products.first()
        assertEquals(1L, first.id)
        assertTrue(first.thumbnail!!.endsWith(".webp"))
    }

    @Test
    fun `missing brand does not fail parsing`() = runTest {
        harness.enqueueJsonFromResource("fixtures/dummyjson_products_missing_brand.json")

        val response = api.getProducts(limit = 10)

        assertEquals(1, response.products.size)
        assertNull(response.products.first().brand)
    }

    @Test
    fun `request includes the select query to shrink the payload`() = runTest {
        harness.enqueueJsonFromResource("fixtures/dummyjson_products.json")

        api.getProducts(limit = 10)

        val request = harness.server.takeRequest()
        assertTrue(request.target.contains("select=title%2Cdescription%2Cprice%2CdiscountPercentage%2Crating%2Cthumbnail%2Ccategory%2Cbrand"))
        assertTrue(request.target.contains("limit=10"))
    }
}
