package com.waynejiang.linefeed.feature.feed

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.waynejiang.linefeed.core.domain.model.ServiceCard
import com.waynejiang.linefeed.core.testing.TestData
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPagingTransformsTest {
    private fun articles(count: Int) = (0 until count).map { TestData.feedArticle(id = it.toLong(), sortIndex = it.toLong()) }

    private suspend fun transform(count: Int, services: List<ServiceCard>, slots: ServiceCardSlots = ServiceCardSlots()) =
        flowOf(PagingData.from(articles(count)))
            .map { it.toFeedItems(services, slots) }
            .asSnapshot()

    @Test
    fun `sortIndex 0 becomes TopStory, the rest ArticleRow`() = runTest {
        val snapshot = transform(count = 3, services = emptyList())

        assertTrue(snapshot[0] is FeedItem.TopStory)
        assertTrue(snapshot[1] is FeedItem.ArticleRow)
        assertTrue(snapshot[2] is FeedItem.ArticleRow)
    }

    @Test
    fun `no service cards inserted when the service list is empty`() = runTest {
        val snapshot = transform(count = 10, services = emptyList())

        assertTrue(snapshot.none { it is FeedItem.Service })
        assertEquals(10, snapshot.size)
    }

    @Test
    fun `service card spliced in before the configured sortIndex`() = runTest {
        val services = listOf(TestData.serviceCard(id = 1))
        val snapshot = transform(count = 5, services = services, slots = ServiceCardSlots(firstAfter = 3, every = 6))

        // article sortIndex 0,1,2 -> service -> article sortIndex 3,4
        val serviceIndex = snapshot.indexOfFirst { it is FeedItem.Service }
        assertEquals(3, serviceIndex)
        val articleAfter = snapshot[serviceIndex + 1] as FeedItem.ArticleRow
        assertEquals(3L, articleAfter.sortIndex)
    }

    @Test
    fun `service slot wraps around with modulo when there are fewer services than slots`() = runTest {
        val services = listOf(TestData.serviceCard(id = 1), TestData.serviceCard(id = 2))
        val snapshot = transform(count = 16, services = services, slots = ServiceCardSlots(firstAfter = 0, every = 6))

        val serviceItems = snapshot.filterIsInstance<FeedItem.Service>()
        assertEquals(listOf(1L, 2L, 1L), serviceItems.map { it.card.id })
    }

    @Test
    fun `every item has a unique key`() = runTest {
        val services = listOf(TestData.serviceCard(id = 1))
        val snapshot = transform(count = 20, services = services)

        val keys = snapshot.map { it.key }
        assertEquals(keys.distinct(), keys)
    }
}
