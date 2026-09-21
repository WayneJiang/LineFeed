package com.waynejiang.linefeed.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.waynejiang.linefeed.R
import com.waynejiang.linefeed.feature.detail.ArticleDetailRoute
import com.waynejiang.linefeed.feature.detail.articleDetailScreen
import com.waynejiang.linefeed.feature.feed.FeedRoute
import com.waynejiang.linefeed.feature.feed.feedScreen
import com.waynejiang.linefeed.feature.saved.SavedRoute
import com.waynejiang.linefeed.feature.saved.savedScreen

private sealed class TopLevelDestination(val route: Any, val labelRes: Int, val icon: ImageVector) {
    data object Reading : TopLevelDestination(FeedRoute, R.string.app_nav_reading, Icons.Filled.Home)
    data object Saved : TopLevelDestination(SavedRoute, R.string.app_nav_saved, Icons.Filled.Bookmark)
}

private val topLevelDestinations = listOf(TopLevelDestination.Reading, TopLevelDestination.Saved)

@Composable
fun LineFeedApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(destination.route::class)
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            feedScreen(
                onArticleClick = { articleId -> navController.navigate(ArticleDetailRoute(articleId)) },
                onOpenSaved = {
                    navController.navigate(SavedRoute) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
            articleDetailScreen(onBack = { navController.popBackStack() })
            savedScreen(onArticleClick = { articleId -> navController.navigate(ArticleDetailRoute(articleId)) })
        }
    }
}
