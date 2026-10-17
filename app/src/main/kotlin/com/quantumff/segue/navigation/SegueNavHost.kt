package com.quantumff.segue.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.navigation
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.feature.album.navigation.albumScreen
import com.quantumff.segue.feature.album.navigation.navigateToAlbum
import com.quantumff.segue.feature.artist.navigation.artistScreen
import com.quantumff.segue.feature.artist.navigation.navigateToArtist
import com.quantumff.segue.feature.downloads.DownloadsTab
import com.quantumff.segue.feature.downloads.navigation.downloadsScreen
import com.quantumff.segue.feature.downloads.navigation.navigateToDownloads
import com.quantumff.segue.feature.home.navigation.HomeRoute
import com.quantumff.segue.feature.home.navigation.albumListScreen
import com.quantumff.segue.feature.home.navigation.homeScreen
import com.quantumff.segue.feature.home.navigation.navigateToAlbumList
import com.quantumff.segue.feature.library.navigation.LibraryRoute
import com.quantumff.segue.feature.library.navigation.genreScreen
import com.quantumff.segue.feature.library.navigation.libraryScreen
import com.quantumff.segue.feature.library.navigation.navigateToGenre
import com.quantumff.segue.feature.login.navigation.loginScreen
import com.quantumff.segue.feature.login.navigation.navigateToLogin
import com.quantumff.segue.feature.nowplaying.navigation.NowPlayingRoute
import com.quantumff.segue.feature.nowplaying.navigation.nowPlayingScreen
import com.quantumff.segue.feature.playlist.navigation.PlaylistsRoute
import com.quantumff.segue.feature.playlist.navigation.navigateToPlaylist
import com.quantumff.segue.feature.playlist.navigation.playlistScreen
import com.quantumff.segue.feature.playlist.navigation.playlistsScreen
import com.quantumff.segue.feature.queue.QueueContent
import com.quantumff.segue.feature.search.navigation.SearchRoute
import com.quantumff.segue.feature.search.navigation.searchScreen
import com.quantumff.segue.feature.settings.navigation.licensesScreen
import com.quantumff.segue.feature.settings.navigation.navigateToLicenses
import com.quantumff.segue.feature.settings.navigation.navigateToSettings
import com.quantumff.segue.feature.settings.navigation.settingsScreen
import com.quantumff.segue.ui.SegueAppState

/**
 * The only place feature graphs meet. Each tab is a nested graph; detail and secondary screens
 * live at the root so any tab can push them onto its own stack. Features navigate through callbacks
 * wired here; they raise play intents through the injected PlayIntents.
 *
 * @param startDestination [HomeGraph], or a login route when no server is configured.
 * @param onAddToPlaylist shows the add-to-playlist sheet for the song IDs.
 */
@Composable
fun SegueNavHost(
    appState: SegueAppState,
    startDestination: Any,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transitions = rememberSegueTransitions(appState)
    val onAccountClick = { appState.navigate { navigateToSettings() } }
    val onAlbumClick = { id: String -> appState.navigate { navigateToAlbum(id) } }
    val onArtistClick = { id: String -> appState.navigate { navigateToArtist(id) } }
    val onPlaylistClick = { id: String -> appState.navigate { navigateToPlaylist(id) } }
    val onGenreClick = { genre: String -> appState.navigate { navigateToGenre(genre) } }
    val onDownloadsClick = { appState.navigate { navigateToDownloads() } }
    val onPinnedClick = { kind: PinKind, id: String ->
        when (kind) {
            PinKind.Album -> onAlbumClick(id)
            PinKind.Playlist -> onPlaylistClick(id)
            PinKind.Artist -> onArtistClick(id)
            PinKind.Song -> Unit
        }
    }
    val onBackClick = appState::navigateBack

    NavHost(
        navController = appState.navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        navigation<HomeGraph>(startDestination = HomeRoute) {
            homeScreen(
                onAccountClick = onAccountClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onGenreClick = onGenreClick,
                onSeeAllClick = { type -> appState.navigate { navigateToAlbumList(type) } },
                onAddToPlaylist = onAddToPlaylist,
            )
        }
        navigation<LibraryGraph>(startDestination = LibraryRoute) {
            libraryScreen(
                onAccountClick = onAccountClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onGenreClick = onGenreClick,
                onDownloadsClick = onDownloadsClick,
                onAddToPlaylist = onAddToPlaylist,
                downloadsTab = { DownloadsTab(onOpen = onPinnedClick) },
            )
        }
        navigation<PlaylistsGraph>(startDestination = PlaylistsRoute) {
            playlistsScreen(
                onAccountClick = onAccountClick,
                onPlaylistClick = onPlaylistClick,
            )
        }
        navigation<SearchGraph>(startDestination = SearchRoute) {
            searchScreen(
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onGenreClick = onGenreClick,
                onAddToPlaylist = onAddToPlaylist,
            )
        }

        albumScreen(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onGenreClick = onGenreClick,
            onAddToPlaylist = onAddToPlaylist,
        )
        artistScreen(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
        )
        playlistScreen(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
        )
        genreScreen(onBackClick = onBackClick, onAlbumClick = onAlbumClick)
        albumListScreen(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
        )
        settingsScreen(
            onBackClick = onBackClick,
            onDownloadsClick = onDownloadsClick,
            onAddServerClick = { appState.navigate { navigateToLogin(canGoBack = true) } },
            onLicensesClick = { appState.navigate { navigateToLicenses() } },
        )
        licensesScreen(onBackClick = onBackClick)
        downloadsScreen(onBackClick = onBackClick, onOpen = onPinnedClick)
        nowPlayingScreen(
            onCollapseClick = onBackClick,
            // Collapse first, so back from the album or artist returns to where the user was.
            onAlbumClick = { id ->
                appState.navigateBack()
                onAlbumClick(id)
            },
            onArtistClick = { id ->
                appState.navigateBack()
                onArtistClick(id)
            },
            onAddToPlaylist = onAddToPlaylist,
            queueContent = { QueueContent() },
        )
        loginScreen(onBackClick = onBackClick, onSignedIn = appState::onSignedIn)
    }
}

private typealias Enter = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias Exit = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

private class SegueTransitions(val enter: Enter, val exit: Exit, val popEnter: Enter, val popExit: Exit)

/**
 * Shared-axis X (fade plus 30dp slide, `long`) forward and back; tab switches crossfade (`medium`).
 * With reduced motion every transition is a `medium` crossfade (DESIGN.md §2.5).
 */
@Composable
private fun rememberSegueTransitions(appState: SegueAppState): SegueTransitions {
    val reduceMotion = LocalReduceMotion.current
    val offset = with(LocalDensity.current) { SegueMotion.sharedAxisOffset.roundToPx() }
    return remember(appState, reduceMotion, offset) {
        val crossIn = fadeIn(SegueMotion.medium())
        val crossOut = fadeOut(SegueMotion.medium())
        val slideIn = { from: Int ->
            slideInHorizontally(SegueMotion.longEnter<IntOffset>()) { from } + fadeIn(SegueMotion.longEnter())
        }
        val slideOut = { to: Int ->
            slideOutHorizontally(SegueMotion.longExit<IntOffset>()) { to } + fadeOut(SegueMotion.longExit())
        }
        // Read at transition time: the flag is set by the navigation that starts the transition.
        val crossfade = { reduceMotion || appState.isTabSwitch }
        // Now Playing slides over the current screen, which stays put underneath.
        val toNowPlaying: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean =
            { targetState.destination.hasRoute(NowPlayingRoute::class) }
        val fromNowPlaying: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean =
            { initialState.destination.hasRoute(NowPlayingRoute::class) }
        SegueTransitions(
            enter = { if (crossfade()) crossIn else slideIn(offset) },
            exit = {
                when {
                    toNowPlaying() -> ExitTransition.KeepUntilTransitionsFinished
                    crossfade() -> crossOut
                    else -> slideOut(-offset)
                }
            },
            popEnter = {
                when {
                    fromNowPlaying() -> EnterTransition.None
                    reduceMotion -> crossIn
                    else -> slideIn(-offset)
                }
            },
            popExit = { if (reduceMotion) crossOut else slideOut(offset) },
        )
    }
}
