package com.quantumff.segue.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.quantumff.segue.core.designsystem.theme.SegueMotion

/**
 * Now Playing over the shell. It slides up from the bottom with the `player` spring and slides back
 * down when it collapses (DESIGN.md §2.5, §4.7). It is not a destination, so the screen behind it
 * stays drawn while it slides or is dragged down.
 *
 * @param onAlbumClick, [onArtistClick], [onPlaylistClick] and [onGenreClick] should collapse Now
 *   Playing before navigating.
 * @param queueContent the Queue panel.
 */
@Composable
fun NowPlayingOverlay(
    visible: Boolean,
    onCollapse: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    queueContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(PLAYER_DAMPING, PLAYER_STIFFNESS, IntOffset.VisibilityThreshold)) { it },
        exit = slideOutVertically(SegueMotion.longExit()) { it } + fadeOut(SegueMotion.longExit()),
        modifier = modifier,
    ) {
        NowPlayingScreen(
            onCollapse = onCollapse,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onGenreClick = onGenreClick,
            onAddToPlaylist = onAddToPlaylist,
            queueContent = queueContent,
        )
    }
}

private const val PLAYER_DAMPING = 0.85f
private const val PLAYER_STIFFNESS = 400f
