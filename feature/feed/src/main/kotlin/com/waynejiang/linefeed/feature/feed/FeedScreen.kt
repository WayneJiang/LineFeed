package com.waynejiang.linefeed.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.waynejiang.linefeed.core.designsystem.component.FullScreenMessage
import com.waynejiang.linefeed.core.designsystem.component.OfflineBanner
import com.waynejiang.linefeed.core.designsystem.format.RelativeTimeFormatter
import com.waynejiang.linefeed.feature.feed.cells.ArticleRowCell
import com.waynejiang.linefeed.feature.feed.cells.FeedFooter
import com.waynejiang.linefeed.feature.feed.cells.ServiceCardCell
import com.waynejiang.linefeed.feature.feed.cells.TopStoryCard
import com.waynejiang.linefeed.feature.feed.cells.WeatherHeroCard
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onArticleClick: (Long) -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyPagingItems = viewModel.feedItems.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pullRequested by rememberSaveable { mutableStateOf(false) }

    val screenState = remember(lazyPagingItems.loadState, lazyPagingItems.itemCount, uiState.isOffline) {
        deriveFeedScreenState(lazyPagingItems.loadState, lazyPagingItems.itemCount, uiState.isOffline)
    }
    val mediatorRefresh = lazyPagingItems.loadState.mediator?.refresh ?: lazyPagingItems.loadState.refresh

    // The coordinator asked us to refresh articles (a foreground/network-restored trigger decided
    // they're stale) — see PLAN.md §3.3. Scroll back to the top: this is a deliberate "come back to
    // the latest" jump, not an accidental one, since it only fires on those two triggers.
    LaunchedEffect(uiState.pendingArticleRefreshId) {
        val id = uiState.pendingArticleRefreshId
        if (id != null) {
            lazyPagingItems.refresh()
            listState.scrollToItem(0)
            viewModel.onArticleRefreshHandled(id)
        }
    }

    LaunchedEffect(pullRequested, mediatorRefresh, uiState.isRefreshingOtherSources) {
        if (pullRequested && mediatorRefresh !is LoadState.Loading && !uiState.isRefreshingOtherSources) {
            pullRequested = false
        }
    }

    LaunchedEffect(screenState.refreshError) {
        val error = screenState.refreshError ?: return@LaunchedEffect
        val ago = uiState.lastUpdated?.let { RelativeTimeFormatter.format(it, Instant.now()) }.orEmpty()
        snackbarHostState.showSnackbar(context.getString(R.string.feed_stale_content_snackbar, ago))
    }

    LaunchedEffect(uiState.userMessage) {
        val message = uiState.userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(message.messageRes, *message.formatArgs.toTypedArray()))
        viewModel.onUserMessageShown(message.id)
    }

    // Network came back while the footer was stuck on Offline/Error: retry automatically instead
    // of making the user pull-to-refresh again.
    LaunchedEffect(uiState.isOffline, screenState.footer) {
        if (!uiState.isOffline && (screenState.footer == FooterState.Offline || screenState.footer == FooterState.Error)) {
            lazyPagingItems.retry()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            androidx.compose.material3.TopAppBar(title = { androidx.compose.material3.Text(stringResource(R.string.feed_title)) })
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = pullRequested,
            onRefresh = {
                pullRequested = true
                lazyPagingItems.refresh()
                viewModel.onPullToRefresh()
            },
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                OfflineBanner(
                    text = stringResource(com.waynejiang.linefeed.core.designsystem.R.string.designsystem_offline_banner),
                    visible = uiState.isOffline && lazyPagingItems.itemCount > 0,
                )

                val fullScreen = screenState.fullScreen
                if (fullScreen != null) {
                    FullScreenState(
                        state = fullScreen,
                        onRetry = { lazyPagingItems.retry() },
                        onOpenSaved = onOpenSaved,
                    )
                } else {
                    FeedContent(
                        weather = uiState.weather,
                        lazyPagingItems = lazyPagingItems,
                        footer = screenState.footer,
                        listState = listState,
                        onArticleClick = onArticleClick,
                        onToggleBookmark = viewModel::onToggleBookmark,
                        onRetryAppend = { lazyPagingItems.retry() },
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenState(state: FullScreenState, onRetry: () -> Unit, onOpenSaved: () -> Unit) {
    when (state) {
        FullScreenState.Loading -> FullScreenMessage(title = "", isLoading = true, modifier = Modifier.fillMaxSize())
        FullScreenState.Offline -> FullScreenMessage(
            title = stringResource(R.string.feed_offline_title),
            body = stringResource(R.string.feed_offline_body),
            icon = Icons.Filled.CloudOff,
            actionLabel = stringResource(R.string.feed_offline_action),
            onAction = onOpenSaved,
            modifier = Modifier.fillMaxSize(),
        )
        FullScreenState.Empty -> FullScreenMessage(
            title = stringResource(R.string.feed_empty_title),
            actionLabel = stringResource(R.string.feed_empty_action),
            onAction = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is FullScreenState.Error -> FullScreenMessage(
            title = stringResource(R.string.feed_error_title),
            body = errorBody(state.error),
            actionLabel = stringResource(R.string.feed_retry),
            onAction = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun errorBody(error: com.waynejiang.linefeed.core.domain.model.AppError): String = when (error) {
    com.waynejiang.linefeed.core.domain.model.AppError.OFFLINE -> stringResource(R.string.feed_error_body_offline)
    com.waynejiang.linefeed.core.domain.model.AppError.TIMEOUT -> stringResource(R.string.feed_error_body_timeout)
    com.waynejiang.linefeed.core.domain.model.AppError.PARSE -> stringResource(R.string.feed_error_body_parse)
    com.waynejiang.linefeed.core.domain.model.AppError.SERVER,
    com.waynejiang.linefeed.core.domain.model.AppError.UNKNOWN,
    -> stringResource(R.string.feed_error_body_unknown)
}

@Composable
private fun FeedContent(
    weather: WeatherCardState,
    lazyPagingItems: androidx.paging.compose.LazyPagingItems<FeedItem>,
    footer: FooterState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onArticleClick: (Long) -> Unit,
    onToggleBookmark: (com.waynejiang.linefeed.core.domain.model.Article) -> Unit,
    onRetryAppend: () -> Unit,
) {
    val now = remember { Instant.now() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "weather") {
            WeatherHeroCard(state = weather, now = now)
        }
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.key },
            contentType = lazyPagingItems.itemContentType { it.contentType },
        ) { index ->
            when (val item = lazyPagingItems[index]) {
                is FeedItem.TopStory -> TopStoryCard(
                    article = item.article,
                    now = now,
                    onClick = { onArticleClick(item.article.id) },
                    onToggleBookmark = { onToggleBookmark(item.article) },
                )
                is FeedItem.ArticleRow -> ArticleRowCell(
                    article = item.article,
                    now = now,
                    onClick = { onArticleClick(item.article.id) },
                    onToggleBookmark = { onToggleBookmark(item.article) },
                )
                is FeedItem.Service -> {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    ServiceCardCell(card = item.card, onClick = { uriHandler.openUri(item.card.actionUrl) })
                }
                null -> Unit
            }
        }
        item(key = "footer") {
            FeedFooter(state = footer, onRetry = onRetryAppend)
        }
    }
}
