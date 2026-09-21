package com.waynejiang.linefeed.feature.saved

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SavedRoute

fun NavGraphBuilder.savedScreen(onArticleClick: (Long) -> Unit) {
    composable<SavedRoute> {
        SavedScreen(onArticleClick = onArticleClick)
    }
}
