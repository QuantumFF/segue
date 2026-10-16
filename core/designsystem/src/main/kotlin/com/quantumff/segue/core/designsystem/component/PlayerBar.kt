package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.designsystem.util.formatDuration
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode

/** Player bar state (expanded width). Position comes separately, as a lambda. */
@Immutable
data class PlayerBarState(
    val track: MiniPlayerState,
    val durationSeconds: Long,
    val shuffleMode: ShuffleMode,
    val repeatMode: RepeatMode,
    val volume: Float,
)

/** Callbacks for [PlayerBar], grouped so the bar takes one stable parameter. */
@Immutable
data class PlayerBarActions(
    val onClick: () -> Unit,
    val onPlayPauseClick: () -> Unit,
    val onPreviousClick: () -> Unit,
    val onNextClick: () -> Unit,
    val onShuffleClick: () -> Unit,
    val onRepeatClick: () -> Unit,
    val onLyricsClick: () -> Unit,
    val onQueueClick: () -> Unit,
    val onMoreClick: () -> Unit,
    val onVolumeChange: (Float) -> Unit,
)

/**
 * Expanded-width player bar (DESIGN.md §5.5), visual only: 80 tall, `surfaceContainer`, an
 * `outlineVariant` top border, and a 1fr : 2fr : 1fr grid (Feishin).
 *
 * @param positionSeconds read by the elapsed-time text and the progress line only.
 */
@Composable
fun PlayerBar(
    state: PlayerBarState,
    positionSeconds: () -> Long,
    actions: PlayerBarActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .height(SegueSizes.playerBar)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        HorizontalDivider(thickness = SegueSizes.hairline, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SegueSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = actions.onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    coverArtId = state.track.coverArtId,
                    displaySize = SegueSizes.playerBarArt,
                    shape = SegueShapes.sm,
                    modifier = Modifier.size(SegueSizes.playerBarArt),
                )
                Spacer(Modifier.width(SegueSpacing.md))
                TrackTitleAndArtist(state = state.track, modifier = Modifier.weight(1f))
            }
            TransportAndSeek(
                state = state,
                positionSeconds = positionSeconds,
                actions = actions,
                modifier = Modifier.weight(2f),
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegueIconButton(
                    icon = SegueIcons.Lyrics,
                    contentDescription = stringResource(R.string.ds_lyrics),
                    onClick = actions.onLyricsClick,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegueIconButton(
                    icon = SegueIcons.UpNext,
                    contentDescription = stringResource(R.string.ds_queue),
                    onClick = actions.onQueueClick,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = SegueIcons.Volume,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
                Slider(
                    value = state.volume,
                    onValueChange = actions.onVolumeChange,
                    modifier = Modifier.width(SegueSizes.volumeSlider),
                )
                SegueIconButton(
                    icon = SegueIcons.MoreVertical,
                    contentDescription = stringResource(R.string.ds_more_options),
                    onClick = actions.onMoreClick,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TransportAndSeek(
    state: PlayerBarState,
    positionSeconds: () -> Long,
    actions: PlayerBarActions,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShuffleIconButton(mode = state.shuffleMode, onClick = actions.onShuffleClick)
            SegueIconButton(
                icon = SegueIcons.SkipPrevious,
                contentDescription = stringResource(R.string.ds_previous),
                onClick = actions.onPreviousClick,
            )
            FilledIconButton(
                onClick = actions.onPlayPauseClick,
                shape = SegueShapes.full,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.size(SegueSizes.playerBarPlay),
            ) {
                Icon(
                    imageVector = if (state.track.isPlaying) SegueIcons.PauseFilled else SegueIcons.PlayFilled,
                    contentDescription = stringResource(
                        if (state.track.isPlaying) R.string.ds_pause else R.string.ds_play,
                    ),
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
            SegueIconButton(
                icon = SegueIcons.SkipNext,
                contentDescription = stringResource(R.string.ds_next),
                onClick = actions.onNextClick,
            )
            SegueIconButton(
                icon = if (state.repeatMode == RepeatMode.One) SegueIcons.RepeatOne else SegueIcons.Repeat,
                contentDescription = stringResource(R.string.ds_repeat),
                onClick = actions.onRepeatClick,
                tint = if (state.repeatMode == RepeatMode.Off) muted else MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ElapsedText(positionSeconds)
            Spacer(Modifier.width(SegueSpacing.sm))
            ProgressLine(
                progress = {
                    if (state.durationSeconds > 0) positionSeconds().toFloat() / state.durationSeconds else 0f
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SegueSpacing.sm))
            Text(
                text = formatDuration(state.durationSeconds),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = muted,
            )
        }
    }
}

/** Its own scope, so the position tick recomposes only this text, once per shown second. */
@Composable
private fun ElapsedText(positionSeconds: () -> Long) {
    val latestPosition by rememberUpdatedState(positionSeconds)
    val seconds by remember { derivedStateOf { latestPosition() } }
    Text(
        text = formatDuration(seconds),
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shuffle toggle for transport rows: muted when off, accent for tracks, album icon for albums. */
@Composable
fun ShuffleIconButton(mode: ShuffleMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SegueIconButton(
        icon = if (mode == ShuffleMode.Albums) SegueIcons.ShuffleAlbums else SegueIcons.Shuffle,
        contentDescription = stringResource(R.string.ds_shuffle),
        onClick = onClick,
        tint = if (mode ==
            ShuffleMode.Off
        ) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = modifier,
    )
}

@PreviewSegueThemes
@Composable
private fun PlayerBarPreview() {
    SeguePreview {
        PlayerBar(
            state = PlayerBarState(
                track = PreviewData.miniPlayer,
                durationSeconds = PREVIEW_DURATION,
                shuffleMode = ShuffleMode.Albums,
                repeatMode = RepeatMode.All,
                volume = PREVIEW_VOLUME,
            ),
            positionSeconds = { PREVIEW_POSITION },
            actions = PlayerBarActions(
                onClick = {},
                onPlayPauseClick = {},
                onPreviousClick = {},
                onNextClick = {},
                onShuffleClick = {},
                onRepeatClick = {},
                onLyricsClick = {},
                onQueueClick = {},
                onMoreClick = {},
                onVolumeChange = {},
            ),
            modifier = Modifier.width(PREVIEW_WIDTH),
        )
    }
}

private const val PREVIEW_DURATION = 255L
private const val PREVIEW_POSITION = 112L
private const val PREVIEW_VOLUME = 0.7f
private val PREVIEW_WIDTH = SegueSizes.sidebar * 4
