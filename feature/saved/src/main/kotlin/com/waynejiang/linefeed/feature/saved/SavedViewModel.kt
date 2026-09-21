package com.waynejiang.linefeed.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waynejiang.linefeed.core.domain.model.Article
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val uiState: StateFlow<SavedUiState> = combine(
        bookmarkRepository.observeSaved(),
        networkMonitor.status,
    ) { items, network ->
        SavedUiState(items = items, isOffline = network == NetworkStatus.OFFLINE, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    fun onRemoveBookmark(article: Article) {
        viewModelScope.launch {
            bookmarkRepository.setBookmarked(article, bookmarked = false)
        }
    }
}
