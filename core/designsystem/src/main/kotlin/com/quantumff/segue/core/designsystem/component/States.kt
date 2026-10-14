package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.coroutines.delay

/**
 * Empty state (DESIGN.md §5.8): centered 48 icon (muted), 16 gap, `titleMedium`, a muted
 * `bodyMedium` message (2 lines max), and an optional tonal button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onActionClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SegueSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(SegueSizes.iconEmptyState),
        )
        Spacer(Modifier.height(SegueSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(SegueSpacing.xs))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(SegueSpacing.lg))
            FilledTonalButton(onClick = onActionClick) { Text(actionLabel) }
        }
    }
}

/** Error with no cached data: the empty state with an `error` icon and a Retry button. */
@Composable
fun ErrorState(title: String, message: String?, onRetryClick: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = SegueIcons.Error,
        title = title,
        message = message,
        iconTint = MaterialTheme.colorScheme.error,
        actionLabel = stringResource(R.string.ds_retry),
        onActionClick = onRetryClick,
        modifier = modifier,
    )
}

/** Inline spinner: 20, stroke 2. */
@Composable
fun InlineSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        strokeWidth = SegueSizes.spinnerStroke,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(SegueSizes.spinner),
    )
}

/**
 * Returns false for the first [delayMillis], then true. Show skeletons only when Room has no data
 * and the load takes longer than 150ms (DESIGN.md §5.8).
 */
@Composable
fun rememberDelayedVisibility(delayMillis: Long = SKELETON_DELAY_MS): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis)
        visible = true
    }
    return visible
}

/**
 * Skeleton pulse: `surfaceContainerHigh` blocks whose alpha pulses 0.5 to 1.0 (1000ms, reverse).
 * No shimmer gradient. One transition drives every block inside, and alpha is applied in the
 * draw phase, so pulsing never recomposes.
 */
@Composable
fun SkeletonPulse(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

/** One placeholder block. Place inside [SkeletonPulse]. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = SegueShapes.xs) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape))
}

/** Skeleton for [TrackRow], using its real layout. */
@Composable
fun TrackRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.trackRow)
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(SegueSizes.trackRowArt), shape = SegueShapes.sm)
        Spacer(Modifier.width(SegueSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SegueSpacing.xs)) {
            SkeletonBlock(Modifier.fillMaxWidth(TITLE_FRACTION).height(SegueSpacing.md))
            SkeletonBlock(Modifier.fillMaxWidth(SUBTITLE_FRACTION).height(SegueSpacing.md))
        }
    }
}

/** Skeleton for [AlbumCard], using its real layout. */
@Composable
fun AlbumCardSkeleton(width: Dp, modifier: Modifier = Modifier) {
    Column(modifier.width(width)) {
        SkeletonBlock(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = SegueShapes.md,
        )
        Spacer(Modifier.height(SegueSpacing.sm))
        SkeletonBlock(Modifier.fillMaxWidth(TITLE_FRACTION).height(SegueSpacing.md))
        Spacer(Modifier.height(SegueSpacing.xs))
        SkeletonBlock(Modifier.fillMaxWidth(SUBTITLE_FRACTION).height(SegueSpacing.md))
    }
}

private const val SKELETON_DELAY_MS = 150L
private const val SKELETON_PULSE_MS = 1000
private const val SKELETON_MIN_ALPHA = 0.5f
private const val TITLE_FRACTION = 0.6f
private const val SUBTITLE_FRACTION = 0.4f

@PreviewSegueThemes
@Composable
private fun EmptyStatePreview() {
    SeguePreview {
        EmptyState(
            icon = SegueIcons.Download,
            title = "No downloads yet",
            message = "Pin albums or playlists to listen offline",
            actionLabel = "Browse albums",
        )
    }
}

@PreviewSegueThemes
@Composable
private fun ErrorStatePreview() {
    SeguePreview {
        ErrorState(title = "Server not reachable", message = "Check the address and your connection", onRetryClick = {})
    }
}

@PreviewSegueThemes
@Composable
private fun SkeletonPreview() {
    SeguePreview {
        SkeletonPulse {
            Column {
                Row(
                    modifier = Modifier.padding(SegueSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
                ) {
                    repeat(2) { AlbumCardSkeleton(width = SegueSizes.albumCard) }
                }
                repeat(3) { TrackRowSkeleton() }
                InlineSpinner(Modifier.padding(SegueSpacing.lg))
            }
        }
    }
}
