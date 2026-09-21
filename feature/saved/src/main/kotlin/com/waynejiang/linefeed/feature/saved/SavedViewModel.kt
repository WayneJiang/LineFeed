package com.waynejiang.linefeed.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val QUERY_DEBOUNCE_MILLIS = 300L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Debounced on the DB query, not on updating `uiState.query` itself: the search field must
    // echo every keystroke immediately (it's driven by `uiState.query` too), only the expensive
    // part — actually re-querying Room and re-filtering the list — waits for the user to pause.
    // A per-value debounce (rather than a flat `debounce(300)`) means the very first, blank query
    // on cold start returns instantly instead of the list start empty for 300ms.
    private val filteredItems = query
        .debounce { current -> if (current.isBlank()) 0L else QUERY_DEBOUNCE_MILLIS }
        .flatMapLatest { current -> bookmarkRepository.observeSaved(current) }

    val uiState: StateFlow<SavedUiState> = combine(
        query,
        filteredItems,
        networkMonitor.status,
    ) { currentQuery, items, network ->
        SavedUiState(
            query = currentQuery,
            items = items,
            isOffline = network == NetworkStatus.OFFLINE,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onRemoveBookmark(article: Article) {
        viewModelScope.launch {
            bookmarkRepository.setBookmarked(article, bookmarked = false)
        }
    }
}
