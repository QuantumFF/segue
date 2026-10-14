package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme

/** Download state shown on art and rows (DESIGN.md §4.9). Null means "not downloaded". */
@Immutable
sealed interface DownloadBadgeState {
    data object Queued : DownloadBadgeState

    data class InProgress(val progress: Float) : DownloadBadgeState

    data object Done : DownloadBadgeState
}

/** 16dp download badge: outline arrow (queued), ring progress, or a filled check in `success`. */
@Composable
fun DownloadBadge(state: DownloadBadgeState, modifier: Modifier = Modifier) {
    val badgeModifier = modifier
        .size(SegueSizes.badge)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest, SegueShapes.full)
    when (state) {
        DownloadBadgeState.Queued -> {
            val label = stringResource(R.string.ds_download_queued)
            Box(badgeModifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = SegueIcons.DownloadQueued,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.badge - SegueSpacing.xs),
                )
            }
        }

        is DownloadBadgeState.InProgress -> {
            val label = stringResource(R.string.ds_downloading)
            CircularProgressIndicator(
                progress = { state.progress },
                strokeWidth = SegueSizes.spinnerStroke,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                gapSize = SegueSpacing.none,
                modifier = badgeModifier.semantics { contentDescription = label },
            )
        }

        DownloadBadgeState.Done -> {
            Icon(
                imageVector = SegueIcons.DownloadDone,
                contentDescription = stringResource(R.string.ds_downloaded),
                tint = SegueTheme.colors.success,
                modifier = badgeModifier,
            )
        }
    }
}

/** Explicit "E" badge: 14 square, radius `xs`. */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ds_explicit_description)
    Box(
        modifier = modifier
            .size(SegueSizes.explicitBadge)
            .background(MaterialTheme.colorScheme.onSurfaceVariant, SegueShapes.xs)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.ds_explicit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

/**
 * Three-bar equalizer for the playing track. Paused draws static bars. Bar heights are read in
 * the draw phase, so animating does not recompose. Compose only for the visible playing row.
 */
@Composable
fun EqualizerBars(animating: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val heights: List<State<Float>> = if (animating) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        EqualizerTimings.map { duration ->
            transition.animateFloat(
                initialValue = EQ_MIN,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
                label = "bar",
            )
        }
    } else {
        StaticHeights
    }
    Canvas(modifier.size(SegueSizes.iconSmall)) {
        val barWidth = SegueSizes.equalizerBar.toPx()
        val gap = (size.width - barWidth * heights.size) / (heights.size - 1)
        heights.forEachIndexed { index, height ->
            val barHeight = size.height * height.value
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private const val EQ_MIN = 0.25f
private val EqualizerTimings = listOf(420, 560, 360)
private val StaticHeights: List<State<Float>> = listOf(0.5f, 0.85f, 0.35f).map {
    object : State<Float> {
        override val value = it
    }
}

@PreviewSegueThemes
@Composable
private fun BadgesPreview() {
    SeguePreview {
        Row(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
            DownloadBadge(DownloadBadgeState.Queued)
            DownloadBadge(DownloadBadgeState.InProgress(0.6f))
            DownloadBadge(DownloadBadgeState.Done)
            ExplicitBadge()
            EqualizerBars(animating = false)
        }
    }
}
