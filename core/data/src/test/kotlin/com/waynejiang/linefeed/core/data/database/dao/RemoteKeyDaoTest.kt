package com.waynejiang.linefeed.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waynejiang.linefeed.core.data.database.LineFeedDatabase
import com.waynejiang.linefeed.core.data.database.createTestDatabase
import com.waynejiang.linefeed.core.data.database.entity.RemoteKeyEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `get returns null when absent`() = runTest {
        assertNull(dao.get(RemoteKeyEntity.ARTICLES_FEED))
    }

    @Test
    fun `upsert then get round-trips, and upsert replaces the previous value`() = runTest {
        dao.upsert(RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, nextCursorPublishedAtMillis = 100, endOfPaginationReached = false, updatedAtMillis = 1))
        assertEquals(100L, dao.get(RemoteKeyEntity.ARTICLES_FEED)?.nextCursorPublishedAtMillis)

        dao.upsert(RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, nextCursorPublishedAtMillis = 50, endOfPaginationReached = true, updatedAtMillis = 2))
        val updated = dao.get(RemoteKeyEntity.ARTICLES_FEED)
        assertEquals(50L, updated?.nextCursorPublishedAtMillis)
        assertEquals(true, updated?.endOfPaginationReached)
    }

    @Test
    fun `clear removes the row`() = runTest {
        dao.upsert(RemoteKeyEntity(RemoteKeyEntity.ARTICLES_FEED, nextCursorPublishedAtMillis = 100, endOfPaginationReached = false, updatedAtMillis = 1))
        dao.clear(RemoteKeyEntity.ARTICLES_FEED)
        assertNull(dao.get(RemoteKeyEntity.ARTICLES_FEED))
    }
}
