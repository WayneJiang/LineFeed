package com.waynejiang.linefeed.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RemoteKeyDaoTest {
    private lateinit var database: LineFeedDatabase
    private lateinit var dao: RemoteKeyDao

    @Before
    fun setUp() {
        database = createTestDatabase()
        dao = database.remoteKeyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `get returns null when no key stored`() = runBlocking {
        assertNull(dao.get(RemoteKeyEntity.ARTICLES_FEED))
    }

    @Test
    fun `upsert inserts then updates the same feed row`() = runBlocking {
        dao.upsert(RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, 1_000L, endOfPaginationReached = false, updatedAtMillis = 1L))
        assertEquals(1_000L, dao.get(RemoteKeyEntity.ARTICLES_FEED)?.nextCursorPublishedAtMillis)

        dao.upsert(RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, 500L, endOfPaginationReached = true, updatedAtMillis = 2L))
        val updated = dao.get(RemoteKeyEntity.ARTICLES_FEED)
        assertEquals(500L, updated?.nextCursorPublishedAtMillis)
        assertTrue(updated?.endOfPaginationReached == true)
    }

    @Test
    fun `clear removes the row for a feed`() = runBlocking {
        dao.upsert(RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, 1_000L, endOfPaginationReached = false, updatedAtMillis = 1L))
        dao.clear(RemoteKeyEntity.ARTICLES_FEED)
        assertNull(dao.get(RemoteKeyEntity.ARTICLES_FEED))
    }
}
