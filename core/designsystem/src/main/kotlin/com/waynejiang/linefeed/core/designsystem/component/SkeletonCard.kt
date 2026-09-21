package com.waynejiang.linefeed.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A pulsing placeholder for one feed-card-shaped slot, shown 3x while the feed's first page loads. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val shimmerColor = shimmerColor()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                SkeletonBlock(shimmerColor, Modifier.size(72.dp), RoundedCornerShape(12.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(shimmerColor, Modifier.fillMaxWidth().height(16.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    SkeletonBlock(shimmerColor, Modifier.fillMaxWidth(0.6f).height(16.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    SkeletonBlock(shimmerColor, Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(color: Color, modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(6.dp)) {
    androidx.compose.foundation.layout.Box(modifier = modifier.clip(shape).background(color))
}

@Composable
private fun shimmerColor(): Color {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeleton-shimmer-alpha",
    )
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.3f)
}
