package com.waynejiang.linefeed.feature.detail

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class ArticleDetailRoute(val articleId: Long)

fun NavGraphBuilder.articleDetailScreen(onBack: () -> Unit) {
    composable<ArticleDetailRoute> {
        ArticleDetailScreen(onBack = onBack)
    }
}
