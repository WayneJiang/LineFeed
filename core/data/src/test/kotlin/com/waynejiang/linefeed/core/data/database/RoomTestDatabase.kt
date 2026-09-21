package com.waynejiang.linefeed.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers

/**
 * In-memory Room DB for Robolectric tests.
 *
 * Deliberately uses the real `Dispatchers.IO` here, not `kotlinx-coroutines-test`'s
 * `UnconfinedTestDispatcher`: Room's generated DAO code calls `withContext(queryCoroutineContext)`
 * internally, and `UnconfinedTestDispatcher.dispatch()` is only meant to be driven by `runTest`'s
 * own coroutine machinery (it throws `UnsupportedOperationException` when a generic `withContext`
 * dispatch reaches it, as Room's does). These tests don't need virtual time control over the DB
 * layer, so a plain real dispatcher avoids the mismatch entirely.
 */
fun createTestDatabase(): LineFeedDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LineFeedDatabase::class.java)
        .setQueryCoroutineContext(Dispatchers.IO)
        .allowMainThreadQueries()
        .build()
