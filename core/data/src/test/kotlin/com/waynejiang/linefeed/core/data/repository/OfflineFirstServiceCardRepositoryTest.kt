package com.waynejiang.linefeed.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.network.ServiceRemoteDataSource
import com.waynejiang.linefeed.core.data.network.dto.ProductDto
import com.waynejiang.linefeed.core.domain.model.ContentSource
import com.waynejiang.linefeed.core.domain.refresh.SourceResult
import com.waynejiang.linefeed.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineFirstServiceCardRepositoryTest {
    private lateinit var database: LineFeedDatabase
    private val clock = FakeClock()

    private fun product(id: Long, discountPercentage: Double = 0.0) = ProductDto(
        id = id,
        title = "Product $id",
        description = "Description $id",
        discountPercentage = discountPercentage,
        thumbnail = "https://example.com/$id.jpg",
    )

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(remoteDataSource: ServiceRemoteDataSource) = OfflineFirstServiceCardRepository(
        serviceCardDao = database.serviceCardDao(),
        remoteDataSource = remoteDataSource,
        syncMetadataDao = database.syncMetadataDao(),
        clock = clock,
    )

    @Test
    fun `refresh replaces the cached set and preserves order`() = runBlocking {
        val repo = repository(fakeSource(listOf(product(1), product(2, discountPercentage = 20.0))))

        val result = repo.refresh()

        assertEquals(SourceResult.Success, result)
        val cards = repo.observeServiceCards().first()
        assertEquals(listOf(1L, 2L), cards.map { it.id })
        assertEquals("Get 20% off", cards[1].ctaLabel)
        assertEquals(clock.now().toEpochMilli(), database.syncMetadataDao().get(ContentSource.SERVICES.name)?.lastSuccessAtMillis)
    }

    @Test
    fun `a second refresh fully replaces the previous set, not merges it`() = runBlocking {
        repository(fakeSource(listOf(product(1), product(2)))).refresh()
        val repo2 = repository(fakeSource(listOf(product(3))))

        repo2.refresh()

        assertEquals(listOf(3L), repo2.observeServiceCards().first().map { it.id })
    }

    @Test
    fun `refresh failure returns Failed and leaves the cache untouched`() = runBlocking {
        repository(fakeSource(listOf(product(1)))).refresh()
        val failing = object : ServiceRemoteDataSource {
            override suspend fun fetchProducts(limit: Int) = throw java.io.IOException("boom")
        }

        val result = repository(failing).refresh()

        assertTrue(result is SourceResult.Failed)
        assertEquals(listOf(1L), database.serviceCardDao().observeAll().first().map { it.id })
    }

    private fun fakeSource(response: List<ProductDto>) = object : ServiceRemoteDataSource {
        override suspend fun fetchProducts(limit: Int): List<ProductDto> = response
    }
}
