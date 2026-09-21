package com.waynejiang.linefeed.feature.feed.cells

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waynejiang.linefeed.core.designsystem.component.BookmarkIconButton
import com.waynejiang.linefeed.core.designsystem.component.FeedImage
import com.waynejiang.linefeed.core.designsystem.component.SourceChip
import com.waynejiang.linefeed.core.designsystem.format.RelativeTimeFormatter
import com.waynejiang.linefeed.core.domain.model.Article
import java.time.Instant

/** The single, larger card for the first article in the feed (`sortIndex == 0`). */
@Composable
fun TopStoryCard(
    article: Article,
    now: Instant,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column {
            FeedImage(
                model = article.imageUrl,
                contentDescription = article.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(Modifier.padding(16.dp)) {
                SourceChip(label = article.newsSite)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        BookmarkIconButton(isBookmarked = article.isBookmarked, onToggle = onToggleBookmark)
                    }
                }
                Text(
                    text = RelativeTimeFormatter.format(article.publishedAt, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
