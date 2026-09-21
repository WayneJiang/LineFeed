package com.waynejiang.linefeed.feature.saved

import com.waynejiang.linefeed.core.domain.model.SavedArticle

data class SavedUiState(
    val query: String = "",
    val items: List<SavedArticle> = emptyList(),
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
)
