package com.waynejiang.linefeed.feature.saved

import com.waynejiang.linefeed.core.domain.model.SavedArticle

/**
 * `query`/debounced `onQueryChange` land in a later step (PLAN.md §10 step 13); kept here already
 * so the type doesn't need to change shape again, but the screen has no search UI yet.
 */
data class SavedUiState(
    val query: String = "",
    val items: List<SavedArticle> = emptyList(),
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
)
