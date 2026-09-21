package com.waynejiang.linefeed.feature.feed

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.waynejiang.linefeed.core.domain.model.AppError

sealed interface FullScreenState {
    data object Loading : FullScreenState
    data object Empty : FullScreenState
    data object Offline : FullScreenState
    data class Error(val error: AppError) : FullScreenState
}

enum class FooterState { None, Loading, Error, Offline, End }

data class FeedScreenState(
    val fullScreen: FullScreenState?,
    val footer: FooterState,
    val refreshError: AppError?,
)

/**
 * Pure translation from Paging's [CombinedLoadStates] to what the screen renders (PLAN.md §5.2).
 * [refresh] prefers `loadStates.mediator?.refresh` over `loadStates.refresh`: the mediator's state
 * reflects the network request `ArticleRemoteMediator` makes, while the plain `source.refresh` only
 * reflects the (near-instant) local Room query.
 */
fun deriveFeedScreenState(
    loadStates: CombinedLoadStates,
    itemCount: Int,
    isOffline: Boolean,
): FeedScreenState {
    val refresh = loadStates.mediator?.refresh ?: loadStates.refresh
    val append = loadStates.append

    val fullScreen: FullScreenState? = if (itemCount == 0) {
        when {
            refresh is LoadState.Loading -> FullScreenState.Loading
            isOffline -> FullScreenState.Offline
            refresh is LoadState.Error -> FullScreenState.Error(refresh.error.toFeedAppError())
            refresh is LoadState.NotLoading && append.endOfPaginationReached -> FullScreenState.Empty
            // First frame, before Paging has reported anything yet: treat as Loading so Empty
            // never flashes before the initial load has actually had a chance to run.
            else -> FullScreenState.Loading
        }
    } else {
        null
    }

    val footer = when {
        append is LoadState.Loading -> FooterState.Loading
        append is LoadState.Error && isOffline -> FooterState.Offline
        append is LoadState.Error -> FooterState.Error
        append is LoadState.NotLoading && append.endOfPaginationReached -> FooterState.End
        else -> FooterState.None
    }

    val refreshError = if (itemCount > 0 && refresh is LoadState.Error) refresh.error.toFeedAppError() else null

    return FeedScreenState(fullScreen = fullScreen, footer = footer, refreshError = refreshError)
}

/**
 * A deliberately smaller classifier than `core:data`'s `Throwable.toAppError()`: `feature:feed`
 * only depends on `core:domain` (no Retrofit on its classpath — see the feature convention
 * plugin's KDoc), so it can't pattern-match `retrofit2.HttpException`. HTTP-status failures fall
 * through to [AppError.UNKNOWN] here; this is a known, minor loss of precision noted in the README.
 */
private fun Throwable.toFeedAppError(): AppError = when (this) {
    is java.net.UnknownHostException, is java.net.ConnectException -> AppError.OFFLINE
    is java.net.SocketTimeoutException -> AppError.TIMEOUT
    is kotlinx.serialization.SerializationException -> AppError.PARSE
    else -> AppError.UNKNOWN
}
