package com.waynejiang.linefeed.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage

/**
 * Every article/service thumbnail goes through this one composable so the placeholder/error look
 * (and later, any Coil configuration change) only needs to change in one place.
 *
 * Uses `SubcomposeAsyncImage` (not a manually-`remember`ed `AsyncImagePainter` read alongside a
 * separate `AsyncImage`): an earlier version did that, and the state-tracking painter was never
 * actually laid out (it existed only to peek at `.state`), so it got zero size constraints, Coil
 * couldn't resolve a target size for it, and it reported `Empty`/`Error` even once the real,
 * visibly-laid-out request had already succeeded — the placeholder icon stayed on screen forever
 * despite the image having loaded (caught during emulator verification, see docs/NOTES.md).
 * `SubcomposeAsyncImage`'s `loading`/`error` slots are Coil's own supported way to do this and
 * don't have that problem.
 */
@Composable
fun FeedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = contentScale,
        loading = { PlaceholderIcon() },
        error = { PlaceholderIcon() },
    )
}

@Composable
private fun PlaceholderIcon() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
