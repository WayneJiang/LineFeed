package com.waynejiang.linefeed.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waynejiang.linefeed.core.designsystem.component.BookmarkIconButton
import com.waynejiang.linefeed.core.designsystem.component.FeedImage
import com.waynejiang.linefeed.core.designsystem.component.FullScreenMessage
import com.waynejiang.linefeed.core.designsystem.component.SourceChip
import com.waynejiang.linefeed.core.designsystem.format.RelativeTimeFormatter
import java.io.File
import java.time.Instant
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArticleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back_content_description))
                    }
                },
                actions = {
                    val content = uiState as? DetailUiState.Content
                    if (content != null) {
                        BookmarkIconButton(isBookmarked = content.article.isBookmarked, onToggle = viewModel::onToggleBookmark)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                DetailUiState.Loading -> DelayedLoadingIndicator()
                DetailUiState.NotFound -> FullScreenMessage(
                    title = stringResource(R.string.detail_not_found_title),
                    body = stringResource(R.string.detail_not_found_body),
                    actionLabel = stringResource(R.string.detail_back_content_description),
                    onAction = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
                is DetailUiState.Content -> ArticleDetailContent(state)
            }
        }
    }
}

/** Avoids a Loading-state flicker: Room reads resolve in well under a frame almost always (PLAN.md §5.3). */
@Composable
private fun DelayedLoadingIndicator() {
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        showSpinner = true
    }
    if (showSpinner) {
        FullScreenMessage(title = "", isLoading = true, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ArticleDetailContent(state: DetailUiState.Content, modifier: Modifier = Modifier) {
    val article = state.article
    val uriHandler = LocalUriHandler.current
    val now = remember { Instant.now() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeedImage(
            model = state.localImagePath?.let(::File) ?: article.imageUrl,
            contentDescription = article.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        SourceChip(label = article.newsSite)
        Text(text = article.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = RelativeTimeFormatter.format(article.publishedAt, now),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (article.authors.isNotEmpty()) {
            Text(
                text = stringResource(R.string.detail_by_authors, article.authors.joinToString(", ")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = article.summary, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { uriHandler.openUri(article.url) },
            enabled = !state.isOffline,
        ) {
            Text(stringResource(if (state.isOffline) R.string.detail_read_original_offline else R.string.detail_read_original))
        }
    }
}
