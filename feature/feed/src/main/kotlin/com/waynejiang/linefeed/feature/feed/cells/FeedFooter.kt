package com.waynejiang.linefeed.feature.feed.cells

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.waynejiang.linefeed.feature.feed.FooterState
import com.waynejiang.linefeed.feature.feed.R

/** The end-of-list row: spinner / retry / offline / "caught up" — never anything for [FooterState.None]. */
@Composable
fun FeedFooter(state: FooterState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (state == FooterState.None) return

    Box(
        modifier = modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            FooterState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            FooterState.Error -> Text(
                text = stringResource(R.string.feed_footer_error),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onRetry),
            )
            FooterState.Offline -> Text(
                text = stringResource(R.string.feed_footer_offline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FooterState.End -> Text(
                text = stringResource(R.string.feed_footer_end),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FooterState.None -> Unit
        }
    }
}
