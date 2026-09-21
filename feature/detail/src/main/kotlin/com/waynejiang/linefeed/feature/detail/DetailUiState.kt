package com.waynejiang.linefeed.feature.detail

import com.waynejiang.linefeed.core.domain.model.Article

/**
 * [Content.localImagePath] (not part of [Article]) is why this screen combines
 * `ArticleRepository.observeArticle` with `BookmarkRepository.observeSavedArticle` rather than
 * reading the article alone: a bookmarked article's own downloaded image is what keeps the detail
 * screen viewable offline after the disposable feed cache has been cleared (PLAN.md §7.6).
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Content(
        val article: Article,
        val isOffline: Boolean,
        val localImagePath: String? = null,
    ) : DetailUiState
}
