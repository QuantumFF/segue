package com.quantumff.segue.feature.nowplaying.navigation

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.feature.nowplaying.NowPlayingScreen
import kotlinx.serialization.Serializable

@Serializable
data object NowPlayingRoute

fun NavController.navigateToNowPlaying(navOptions: NavOptions? = null) = navigate(NowPlayingRoute, navOptions)

/**
 * Now Playing slides up from the bottom with the `player` spring and slides back down when it
 * collapses (DESIGN.md §2.5, §4.7). The screens behind it stay put.
 *
 * @param onAlbumClick and [onArtistClick] should collapse Now Playing before navigating.
 * @param queueContent the Up next panel.
 */
fun NavGraphBuilder.nowPlayingScreen(
    onCollapseClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    queueContent: @Composable () -> Unit,
) {
    composable<NowPlayingRoute>(
        enterTransition = { slideInVertically(spring(PLAYER_DAMPING, PLAYER_STIFFNESS, IntOffset.VisibilityThreshold)) { it } },
        exitTransition = { fadeOut(SegueMotion.medium()) },
        popEnterTransition = { fadeIn(SegueMotion.medium()) },
        popExitTransition = { slideOutVertically(SegueMotion.longExit()) { it } + fadeOut(SegueMotion.longExit()) },
    ) {
        NowPlayingScreen(
            onCollapse = onCollapseClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
            queueContent = queueContent,
        )
    }
}

private const val PLAYER_DAMPING = 0.85f
private const val PLAYER_STIFFNESS = 400f
