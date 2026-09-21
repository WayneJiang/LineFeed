package com.waynejiang.linefeed.feature.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waynejiang.linefeed.core.designsystem.component.BookmarkIconButton
import com.waynejiang.linefeed.core.designsystem.component.FeedImage
import com.waynejiang.linefeed.core.designsystem.component.FullScreenMessage
import com.waynejiang.linefeed.core.designsystem.component.OfflineBanner
import com.waynejiang.linefeed.core.designsystem.format.RelativeTimeFormatter
import com.waynejiang.linefeed.core.domain.model.SavedArticle
import java.io.File
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.saved_title)) }) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            OfflineBanner(text = stringResource(R.string.saved_offline_banner), visible = uiState.isOffline)

            when {
                uiState.isLoading -> FullScreenMessage(title = "", isLoading = true, modifier = Modifier.fillMaxSize())
                uiState.items.isEmpty() -> FullScreenMessage(
                    title = stringResource(R.string.saved_empty_title),
                    body = stringResource(R.string.saved_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> SavedContent(
                    items = uiState.items,
                    onArticleClick = onArticleClick,
                    onRemoveBookmark = viewModel::onRemoveBookmark,
                )
            }
        }
    }
}

@Composable
private fun SavedContent(
    items: List<SavedArticle>,
    onArticleClick: (Long) -> Unit,
    onRemoveBookmark: (com.waynejiang.linefeed.core.domain.model.Article) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Instant.now()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = items, key = { it.article.id }) { saved ->
            SavedArticleRow(
                saved = saved,
                now = now,
                onClick = { onArticleClick(saved.article.id) },
                onRemoveBookmark = { onRemoveBookmark(saved.article) },
            )
        }
    }
}

@Composable
private fun SavedArticleRow(
    saved: SavedArticle,
    now: Instant,
    onClick: () -> Unit,
    onRemoveBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${saved.article.newsSite} · ${RelativeTimeFormatter.format(saved.savedAt, now)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = saved.article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FeedImage(
            model = saved.localImagePath?.let(::File) ?: saved.article.imageUrl,
            contentDescription = saved.article.title,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).padding(start = 8.dp),
        )
        BookmarkIconButton(isBookmarked = true, onToggle = onRemoveBookmark)
    }
}
