package com.quantumff.segue.feature.playlist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.feature.playlist.PlaylistScreen
import com.quantumff.segue.feature.playlist.PlaylistsScreen
import kotlinx.serialization.Serializable

/** The Playlists tab root. */
@Serializable
data object PlaylistsRoute

/** [playlistId] is read by name from the SavedStateHandle (PlaylistViewModel.PLAYLIST_ID_KEY). */
@Serializable
data class PlaylistRoute(val playlistId: String)

fun NavController.navigateToPlaylist(playlistId: String, navOptions: NavOptions? = null) =
    navigate(PlaylistRoute(playlistId), navOptions)

fun NavGraphBuilder.playlistsScreen(
    onAccountClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    playActions: PlayActions,
) {
    composable<PlaylistsRoute> {
        PlaylistsScreen(onAccountClick = onAccountClick, onPlaylistClick = onPlaylistClick, playActions = playActions)
    }
}

fun NavGraphBuilder.playlistScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    playActions: PlayActions,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    composable<PlaylistRoute> {
        PlaylistScreen(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            playActions = playActions,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
