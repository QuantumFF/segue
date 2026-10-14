package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/** What the mini player and the player bar show about the current track. */
@Immutable
data class MiniPlayerState(val title: String, val artist: String, val coverArtId: String?, val isPlaying: Boolean)

/**
 * Mini player (DESIGN.md §3, §5.5), visual only: 64 tall, `surfaceContainer`, a 2dp progress
 * line on its top edge. Gestures (drag to expand, swipe to skip) arrive with Now Playing.
 *
 * @param progress playback progress in [0, 1]. Read in the draw phase, so the 1Hz ticker does not
 *   recompose the bar.
 */
@Composable
fun MiniPlayer(
    state: MiniPlayerState,
    progress: () -> Float,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.miniPlayer)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .rowClickable(enabled = true, onClick = onClick, onLongClick = onLongClick),
    ) {
        ProgressLine(progress = progress, modifier = Modifier.align(Alignment.TopCenter))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(start = SegueSpacing.lg, end = SegueSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                coverArtId = state.coverArtId,
                displaySize = SegueSizes.miniPlayerArt,
                shape = SegueShapes.sm,
                modifier = Modifier.size(SegueSizes.miniPlayerArt),
            )
            Spacer(Modifier.width(SegueSpacing.md))
            TrackTitleAndArtist(state = state, modifier = Modifier.weight(1f))
            PlayPauseButton(isPlaying = state.isPlaying, onClick = onPlayPauseClick)
            SegueIconButton(
                icon = SegueIcons.SkipNext,
                contentDescription = stringResource(R.string.ds_next),
                onClick = onNextClick,
            )
        }
    }
}

/** A 2dp line: `primary` progress over an `outlineVariant` track. */
@Composable
internal fun ProgressLine(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = progress,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeCap = StrokeCap.Butt,
        gapSize = SegueSpacing.none,
        drawStopIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.progressLine),
    )
}

@Composable
internal fun TrackTitleAndArtist(state: MiniPlayerState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = state.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SegueIconButton(
        icon = if (isPlaying) SegueIcons.PauseFilled else SegueIcons.PlayFilled,
        contentDescription = stringResource(if (isPlaying) R.string.ds_pause else R.string.ds_play),
        onClick = onClick,
        modifier = modifier,
    )
}

@PreviewSegueThemes
@Composable
private fun MiniPlayerPreview() {
    SeguePreview {
        MiniPlayer(
            state = PreviewData.miniPlayer,
            progress = { PREVIEW_PROGRESS },
            onClick = {},
            onPlayPauseClick = {},
            onNextClick = {},
        )
    }
}

private const val PREVIEW_PROGRESS = 0.4f
