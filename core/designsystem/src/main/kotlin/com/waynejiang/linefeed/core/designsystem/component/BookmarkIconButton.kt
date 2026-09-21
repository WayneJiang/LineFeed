package com.waynejiang.linefeed.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waynejiang.linefeed.core.designsystem.R

/** Solid/outline crossfade (PLAN.md §13's "小動畫") so the toggle reads as a state change, not a jump-cut. */
@Composable
fun BookmarkIconButton(
    isBookmarked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescription = if (isBookmarked) {
        androidx.compose.ui.res.stringResource(R.string.designsystem_bookmark_remove)
    } else {
        androidx.compose.ui.res.stringResource(R.string.designsystem_bookmark_add)
    }
    IconButton(onClick = onToggle, modifier = modifier) {
        Crossfade(targetState = isBookmarked, label = "bookmark-icon") { bookmarked ->
            Icon(
                imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = contentDescription,
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
