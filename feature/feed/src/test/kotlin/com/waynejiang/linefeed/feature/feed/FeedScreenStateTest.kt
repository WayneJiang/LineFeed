package com.waynejiang.linefeed.feature.feed

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import com.waynejiang.linefeed.core.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedScreenStateTest {
    private fun states(
        refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
        append: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
        mediatorRefresh: LoadState? = null,
    ) = CombinedLoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = append,
        source = LoadStates(refresh = refresh, prepend = LoadState.NotLoading(endOfPaginationReached = true), append = append),
        mediator = mediatorRefresh?.let { LoadStates(refresh = it, prepend = LoadState.NotLoading(endOfPaginationReached = true), append = append) },
    )

    @Test
    fun `empty list, mediator refresh loading, is FullScreen Loading`() {
        val result = deriveFeedScreenState(states(mediatorRefresh = LoadState.Loading), itemCount = 0, isOffline = false)
        assertEquals(FullScreenState.Loading, result.fullScreen)
    }

    @Test
    fun `empty list, offline, is FullScreen Offline`() {
        val result = deriveFeedScreenState(states(), itemCount = 0, isOffline = true)
        assertEquals(FullScreenState.Offline, result.fullScreen)
    }

    @Test
    fun `empty list, mediator refresh error and online, is FullScreen Error`() {
        val error = java.io.IOException("boom")
        val result = deriveFeedScreenState(states(mediatorRefresh = LoadState.Error(error)), itemCount = 0, isOffline = false)
        assertEquals(FullScreenState.Error(AppError.UNKNOWN), result.fullScreen)
    }

    @Test
    fun `empty list, refresh done and end of pagination reached, is FullScreen Empty`() {
        val result = deriveFeedScreenState(
            states(refresh = LoadState.NotLoading(endOfPaginationReached = false), append = LoadState.NotLoading(endOfPaginationReached = true)),
            itemCount = 0,
            isOffline = false,
        )
        assertEquals(FullScreenState.Empty, result.fullScreen)
    }

    @Test
    fun `empty list, nothing reported yet, defaults to Loading rather than flashing Empty`() {
        val result = deriveFeedScreenState(states(), itemCount = 0, isOffline = false)
        assertEquals(FullScreenState.Loading, result.fullScreen)
    }

    @Test
    fun `non-empty list never has a full screen state`() {
        val result = deriveFeedScreenState(states(mediatorRefresh = LoadState.Error(java.io.IOException())), itemCount = 5, isOffline = false)
        assertNull(result.fullScreen)
    }

    @Test
    fun `non-empty list with a refresh error surfaces refreshError for the snackbar`() {
        val result = deriveFeedScreenState(states(mediatorRefresh = LoadState.Error(java.io.IOException())), itemCount = 5, isOffline = false)
        assertEquals(AppError.UNKNOWN, result.refreshError)
    }

    @Test
    fun `non-empty list without a refresh error has a null refreshError`() {
        val result = deriveFeedScreenState(states(mediatorRefresh = LoadState.NotLoading(false)), itemCount = 5, isOffline = false)
        assertNull(result.refreshError)
    }

    @Test
    fun `footer Loading while append is loading`() {
        val result = deriveFeedScreenState(states(append = LoadState.Loading), itemCount = 5, isOffline = false)
        assertEquals(FooterState.Loading, result.footer)
    }

    @Test
    fun `footer Offline when append errors while offline`() {
        val result = deriveFeedScreenState(states(append = LoadState.Error(java.io.IOException())), itemCount = 5, isOffline = true)
        assertEquals(FooterState.Offline, result.footer)
    }

    @Test
    fun `footer Error when append errors while online`() {
        val result = deriveFeedScreenState(states(append = LoadState.Error(java.io.IOException())), itemCount = 5, isOffline = false)
        assertEquals(FooterState.Error, result.footer)
    }

    @Test
    fun `footer End when append reaches end of pagination`() {
        val result = deriveFeedScreenState(states(append = LoadState.NotLoading(endOfPaginationReached = true)), itemCount = 5, isOffline = false)
        assertEquals(FooterState.End, result.footer)
    }

    @Test
    fun `footer None otherwise`() {
        val result = deriveFeedScreenState(states(append = LoadState.NotLoading(endOfPaginationReached = false)), itemCount = 5, isOffline = false)
        assertEquals(FooterState.None, result.footer)
    }

    @Test
    fun `refresh prefers mediator state over source state`() {
        // source.refresh is NotLoading (the local Room query resolved instantly) but the mediator's
        // network refresh is still Loading -> the screen must still show Loading, not Empty.
        val result = deriveFeedScreenState(states(refresh = LoadState.NotLoading(false), mediatorRefresh = LoadState.Loading), itemCount = 0, isOffline = false)
        assertEquals(FullScreenState.Loading, result.fullScreen)
    }
}
