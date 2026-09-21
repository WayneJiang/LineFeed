package com.waynejiang.linefeed.feature.feed

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object FeedRoute

fun NavGraphBuilder.feedScreen(onArticleClick: (Long) -> Unit, onOpenSaved: () -> Unit) {
    composable<FeedRoute> {
        FeedScreen(onArticleClick = onArticleClick, onOpenSaved = onOpenSaved)
    }
}
