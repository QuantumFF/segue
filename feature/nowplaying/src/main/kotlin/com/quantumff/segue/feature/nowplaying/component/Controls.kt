package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.feature.nowplaying.NowPlayingPanel
import com.quantumff.segue.feature.nowplaying.R
import kotlinx.coroutines.delay

/**
 * Transport row (DESIGN.md §4.7): shuffle mode · previous · play/pause (72 white circle) · next ·
 * repeat. A label naming the new shuffle mode shows above the row for 1.5s after it changes.
 */
@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    onShuffleClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButton(
                icon = if (shuffleMode == ShuffleMode.Albums) SegueIcons.ShuffleAlbums else SegueIcons.Shuffle,
                active = shuffleMode != ShuffleMode.Off,
                contentDescription = stringResource(shuffleMode.label),
                onClick = onShuffleClick,
            )
            TransportButton(
                icon = SegueIcons.SkipPrevious,
                contentDescription = stringResource(R.string.feature_nowplaying_previous),
                onClick = onPreviousClick,
            )
            FilledIconButton(
                onClick = onPlayPauseClick,
                shape = SegueShapes.full,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = SegueTheme.colors.onArt,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.size(PLAY_BUTTON),
            ) {
                Icon(
                    imageVector = if (isPlaying) SegueIcons.PauseFilled else SegueIcons.PlayFilled,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.feature_nowplaying_pause else R.string.feature_nowplaying_play,
                    ),
                    modifier = Modifier.size(SegueSizes.iconTransport),
                )
            }
            TransportButton(
                icon = SegueIcons.SkipNext,
                contentDescription = stringResource(R.string.feature_nowplaying_next),
                onClick = onNextClick,
            )
            ModeButton(
                icon = if (repeatMode == RepeatMode.One) SegueIcons.RepeatOne else SegueIcons.Repeat,
                active = repeatMode != RepeatMode.Off,
                contentDescription = stringResource(repeatMode.label),
                onClick = onRepeatClick,
            )
        }
        ShuffleModeLabel(
            shuffleMode = shuffleMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = -SegueSpacing.xl),
        )
    }
}

/** "Shuffle albums" for 1.5s after the mode changes; nothing on first show (DESIGN.md §4.7). */
@Composable
private fun ShuffleModeLabel(shuffleMode: ShuffleMode, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<ShuffleMode?>(null) }
    var previous by remember { mutableStateOf(shuffleMode) }
    LaunchedEffect(shuffleMode) {
        if (shuffleMode == previous) return@LaunchedEffect
        previous = shuffleMode
        shown = shuffleMode
        delay(LABEL_MILLIS)
        shown = null
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(SegueMotion.medium()),
        exit = fadeOut(SegueMotion.medium()),
        modifier = modifier,
    ) {
        val mode = shown ?: shuffleMode
        Text(
            text = stringResource(mode.label),
            style = MaterialTheme.typography.labelMedium,
            color = SegueTheme.colors.onArt,
            modifier = Modifier
                .background(Color.Black.copy(alpha = LABEL_BACKGROUND_ALPHA), SegueShapes.full)
                .padding(horizontal = SegueSpacing.sm, vertical = SegueSpacing.xxs),
        )
    }
}

/** Previous/next: a 28 icon in a 56 target. */
@Composable
private fun TransportButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(TRANSPORT_TARGET)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = SegueTheme.colors.onArt,
            modifier = Modifier.size(SegueSizes.iconTransport),
        )
    }
}

/** Shuffle and repeat: white 50% when off; white with a 4dp dot under the icon when on. */
@Composable
private fun ModeButton(icon: ImageVector, active: Boolean, contentDescription: String, onClick: () -> Unit) {
    val onArt = SegueTheme.colors.onArt
    IconButton(onClick = onClick, modifier = Modifier.size(SegueSizes.touchTarget)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) onArt else onArt.copy(alpha = OFF_ALPHA),
            )
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = DOT_OFFSET)
                        .size(DOT)
                        .background(onArt, SegueShapes.full),
                )
            }
        }
    }
}

/**
 * Bottom row of the phone layout: Lyrics · Up next · Related · ⋮, each 48 tall with a label.
 * The open panel's button is full white; tapping it again closes the panel. ⋮ shows a dot while
 * the sleep timer runs.
 */
@Composable
internal fun PanelBar(
    openPanel: NowPlayingPanel?,
    sleepTimerActive: Boolean,
    onPanelClick: (NowPlayingPanel) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        NowPlayingPanel.entries.forEach { panel ->
            PanelBarItem(
                icon = panel.icon,
                label = stringResource(panel.label),
                selected = panel == openPanel,
                onClick = { onPanelClick(panel) },
            )
        }
        PanelBarItem(
            icon = SegueIcons.MoreVertical,
            label = stringResource(R.string.feature_nowplaying_more),
            selected = false,
            onClick = onMoreClick,
            badge = sleepTimerActive,
        )
    }
}

@Composable
private fun PanelBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
    val onArt = SegueTheme.colors.onArt
    val color = if (selected) onArt else SegueTheme.colors.onArtMuted
    Column(
        modifier = Modifier
            .size(width = PANEL_ITEM_WIDTH, height = PANEL_ITEM_HEIGHT)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(bounded = false, radius = PANEL_ITEM_WIDTH / 2),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(DOT + SegueSpacing.xxs)
                        .background(MaterialTheme.colorScheme.primary, SegueShapes.full),
                )
            }
        }
        Spacer(Modifier.height(SegueSpacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(),
        )
    }
}

internal val NowPlayingPanel.label: Int
    get() = when (this) {
        NowPlayingPanel.Lyrics -> R.string.feature_nowplaying_panel_lyrics
        NowPlayingPanel.UpNext -> R.string.feature_nowplaying_panel_up_next
        NowPlayingPanel.Related -> R.string.feature_nowplaying_panel_related
    }

internal val NowPlayingPanel.icon: ImageVector
    get() = when (this) {
        NowPlayingPanel.Lyrics -> SegueIcons.Lyrics
        NowPlayingPanel.UpNext -> SegueIcons.UpNext
        NowPlayingPanel.Related -> SegueIcons.Related
    }

private val ShuffleMode.label: Int
    get() = when (this) {
        ShuffleMode.Off -> R.string.feature_nowplaying_shuffle_off
        ShuffleMode.Tracks -> R.string.feature_nowplaying_shuffle_tracks
        ShuffleMode.Albums -> R.string.feature_nowplaying_shuffle_albums
    }

private val RepeatMode.label: Int
    get() = when (this) {
        RepeatMode.Off -> R.string.feature_nowplaying_repeat_off
        RepeatMode.All -> R.string.feature_nowplaying_repeat_all
        RepeatMode.One -> R.string.feature_nowplaying_repeat_one
    }

private val PLAY_BUTTON = 72.dp
private val TRANSPORT_TARGET = 56.dp
private val DOT = 4.dp
private val DOT_OFFSET = 8.dp
private val PANEL_ITEM_WIDTH = 72.dp
private val PANEL_ITEM_HEIGHT = 56.dp
private const val OFF_ALPHA = 0.5f
private const val LABEL_MILLIS = 1_500L
private const val LABEL_BACKGROUND_ALPHA = 0.5f
