package com.waynejiang.linefeed.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.waynejiang.linefeed.core.designsystem.theme.LineFeedTheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FullScreenMessagePreview() {
    LineFeedTheme {
        Surface {
            FullScreenMessage(
                title = "You're offline",
                body = "Can't load the latest articles right now.",
                icon = Icons.Filled.CloudOff,
                actionLabel = "Go to Saved",
                onAction = {},
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBannerPreview() {
    LineFeedTheme {
        Surface {
            OfflineBanner(text = "You're offline — showing saved content", visible = true)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarkIconButtonPreview() {
    var bookmarked by remember { mutableStateOf(false) }
    LineFeedTheme {
        Surface {
            BookmarkIconButton(isBookmarked = bookmarked, onToggle = { bookmarked = !bookmarked })
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SourceChipPreview() {
    LineFeedTheme {
        Surface(Modifier.padding(8.dp)) {
            SourceChip(label = "Spaceflight Now")
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkeletonCardPreview() {
    LineFeedTheme {
        Surface {
            Column(Modifier.padding(16.dp)) {
                SkeletonCard()
            }
        }
    }
}
