package com.quantumff.segue.core.designsystem.component

import androidx.annotation.StringRes
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
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
import kotlin.math.roundToInt

/** Player bar state (expanded width). Position comes separately, as a lambda. */
@Immutable
data class PlayerBarState(
    val track: MiniPlayerState,
    val durationMillis: Long,
    val repeatMode: RepeatMode,
    /** Media volume, 0 to 1. */
    val volume: Float,
    /** Stops between the volume slider's ends, so the thumb lands on the device's volume steps. */
    val volumeSteps: Int = 0,
)

/** Callbacks for [PlayerBar], grouped so the bar takes one stable parameter. */
@Immutable
data class PlayerBarActions(
    val onClick: () -> Unit,
    val onPlayPauseClick: () -> Unit,
    val onPreviousClick: () -> Unit,
    val onNextClick: () -> Unit,
    val onRepeatClick: () -> Unit,
    val onLyricsClick: () -> Unit,
    val onQueueClick: () -> Unit,
    val onMoreClick: () -> Unit,
    val onVolumeChange: (Float) -> Unit,
    /** Seeks the current song, in milliseconds: the seek line. */
    val onSeek: (Long) -> Unit,
)

/**
 * Expanded-width player bar (DESIGN.md §5.5), visual only: 80 tall, `surfaceContainer`, an
 * `outlineVariant` top border, and a 1fr : 2fr : 1fr grid (Feishin).
 *
 * @param positionMillis read by the elapsed-time text and the seek line only.
 */
@Composable
fun PlayerBar(
    state: PlayerBarState,
    positionMillis: () -> Long,
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
                positionMillis = positionMillis,
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
                VolumeSlider(
                    volume = state.volume,
                    steps = state.volumeSteps,
                    onVolumeChange = actions.onVolumeChange,
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
    positionMillis: () -> Long,
    actions: PlayerBarActions,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val repeatState = stringResource(state.repeatMode.stateLabel)
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Row(
            // Above the seek row, so these buttons keep the touches where the seek line's 48 overlaps them.
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegueIconButton(
                icon = SegueIcons.SkipPrevious,
                contentDescription = stringResource(R.string.ds_previous),
                onClick = actions.onPreviousClick,
            )
            BufferingRing(
                isBuffering = state.track.isBuffering,
                diameter = SegueSizes.playerBarBufferingRing,
                color = MaterialTheme.colorScheme.onSurface,
            ) {
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
                modifier = Modifier.semantics { stateDescription = repeatState },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ElapsedText(positionMillis)
            Spacer(Modifier.width(SegueSpacing.sm))
            SeekLine(
                positionMillis = positionMillis,
                durationMillis = state.durationMillis,
                isPlaying = state.track.isPlaying,
                onSeek = actions.onSeek,
                style = SeekLineStyle.thin(
                    active = MaterialTheme.colorScheme.primary,
                    inactive = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SegueSpacing.sm))
            Text(
                text = formatDuration(state.durationMillis / MILLIS_PER_SECOND),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = muted,
            )
        }
    }
}

/**
 * Named "Volume" for screen readers, which read its level as a percentage. It snaps to [steps]
 * without drawing them: a tick per volume step crowds a 96 slider.
 */
@Composable
private fun VolumeSlider(volume: Float, steps: Int, onVolumeChange: (Float) -> Unit) {
    val label = stringResource(R.string.ds_volume)
    val level = stringResource(R.string.ds_volume_level, (volume * PERCENT).roundToInt())
    Slider(
        value = volume,
        onValueChange = onVolumeChange,
        steps = steps,
        colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        modifier = Modifier
            .width(SegueSizes.volumeSlider)
            .semantics {
                contentDescription = label
                stateDescription = level
            },
    )
}

/** Its own scope, so the position tick recomposes only this text, once per shown second. */
@Composable
private fun ElapsedText(positionMillis: () -> Long) {
    val latestPosition by rememberUpdatedState(positionMillis)
    val seconds by remember { derivedStateOf { latestPosition() / MILLIS_PER_SECOND } }
    Text(
        text = formatDuration(seconds),
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@PreviewSegueThemes
@Composable
private fun PlayerBarPreview() {
    SeguePreview {
        PlayerBar(
            state = PlayerBarState(
                track = PreviewData.miniPlayer,
                durationMillis = PREVIEW_DURATION,
                repeatMode = RepeatMode.All,
                volume = PREVIEW_VOLUME,
            ),
            positionMillis = { PREVIEW_POSITION },
            actions = PlayerBarActions(
                onClick = {},
                onPlayPauseClick = {},
                onPreviousClick = {},
                onNextClick = {},
                onRepeatClick = {},
                onLyricsClick = {},
                onQueueClick = {},
                onMoreClick = {},
                onVolumeChange = {},
                onSeek = {},
            ),
            modifier = Modifier.width(PREVIEW_WIDTH),
        )
    }
}

/** The repeat button's state for screen readers, "Off", "All" or "One"; its name is [R.string.ds_repeat]. */
@get:StringRes
val RepeatMode.stateLabel: Int
    get() = when (this) {
        RepeatMode.Off -> R.string.ds_repeat_off
        RepeatMode.All -> R.string.ds_repeat_all
        RepeatMode.One -> R.string.ds_repeat_one
    }

private const val MILLIS_PER_SECOND = 1_000L
private const val PERCENT = 100
private const val PREVIEW_DURATION = 255_000L
private const val PREVIEW_POSITION = 112_000L
private const val PREVIEW_VOLUME = 0.7f
private val PREVIEW_WIDTH = SegueSizes.sidebar * 4
