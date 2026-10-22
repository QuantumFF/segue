package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.component.BufferingRing
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.feature.nowplaying.NowPlayingPanel
import com.quantumff.segue.feature.nowplaying.R

/**
 * Transport row (DESIGN.md §4.7): previous · play/pause (72 white circle) · next · repeat. There is
 * no shuffle button: music is shuffled when it is queued, never once it plays. An empty slot on
 * the left balances repeat, so play/pause stays centered.
 */
@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    repeatMode: RepeatMode,
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
            Spacer(Modifier.size(SegueSizes.touchTarget))
            TransportButton(
                icon = SegueIcons.SkipPrevious,
                contentDescription = stringResource(R.string.feature_nowplaying_previous),
                onClick = onPreviousClick,
            )
            BufferingRing(
                isBuffering = isBuffering,
                diameter = PLAY_BUTTON_RING,
                color = SegueTheme.colors.onArt,
            ) {
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

/** Repeat: white 50% when off; white with a 4dp dot under the icon when on. */
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
 * Bottom row of the phone layout: Lyrics · Queue · Related · ⋮, each 48 tall with a label.
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
        NowPlayingPanel.Queue -> R.string.feature_nowplaying_panel_queue
        NowPlayingPanel.Related -> R.string.feature_nowplaying_panel_related
    }

internal val NowPlayingPanel.icon: ImageVector
    get() = when (this) {
        NowPlayingPanel.Lyrics -> SegueIcons.Lyrics
        NowPlayingPanel.Queue -> SegueIcons.UpNext
        NowPlayingPanel.Related -> SegueIcons.Related
    }

private val RepeatMode.label: Int
    get() = when (this) {
        RepeatMode.Off -> R.string.feature_nowplaying_repeat_off
        RepeatMode.All -> R.string.feature_nowplaying_repeat_all
        RepeatMode.One -> R.string.feature_nowplaying_repeat_one
    }

private val PLAY_BUTTON = 72.dp

/** Around the play circle, 2 clear of it. */
private val PLAY_BUTTON_RING = 80.dp
private val TRANSPORT_TARGET = 56.dp
private val DOT = 4.dp
private val DOT_OFFSET = 8.dp
private val PANEL_ITEM_WIDTH = 72.dp
private val PANEL_ITEM_HEIGHT = 56.dp
private const val OFF_ALPHA = 0.5f
