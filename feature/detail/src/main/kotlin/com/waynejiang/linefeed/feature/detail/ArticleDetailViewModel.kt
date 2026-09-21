package com.waynejiang.linefeed.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waynejiang.linefeed.core.domain.model.NetworkStatus
import com.waynejiang.linefeed.core.domain.network.NetworkMonitor
import com.waynejiang.linefeed.core.domain.repository.ArticleRepository
import com.waynejiang.linefeed.core.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    // Not `savedStateHandle.toRoute<ArticleDetailRoute>()`: that requires an Android Bundle/
    // navigation runtime, which JVM ViewModel tests don't have (PLAN.md §11). The type-safe nav arg
    // still lands under its property name, so a plain lookup works in both real and test contexts.
    private val articleId: Long = checkNotNull(savedStateHandle.get<Long>("articleId")) { "articleId is required" }

    val uiState: StateFlow<DetailUiState> = combine(
        articleRepository.observeArticle(articleId),
        bookmarkRepository.observeSavedArticle(articleId),
        networkMonitor.status,
    ) { cachedArticle, saved, network ->
        val article = cachedArticle ?: saved?.article
        when (article) {
            null -> DetailUiState.NotFound
            else -> DetailUiState.Content(
                article = article.copy(isBookmarked = saved != null),
                isOffline = network == NetworkStatus.OFFLINE,
                localImagePath = saved?.localImagePath,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    fun onToggleBookmark() {
        val content = uiState.value as? DetailUiState.Content ?: return
        viewModelScope.launch {
            bookmarkRepository.setBookmarked(content.article, bookmarked = !content.article.isBookmarked)
        }
    }
}
