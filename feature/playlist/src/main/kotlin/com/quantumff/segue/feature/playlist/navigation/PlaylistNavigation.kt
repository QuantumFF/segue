package com.quantumff.segue.feature.playlist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.quantumff.segue.feature.playlist.PlaylistScreen
import com.quantumff.segue.feature.playlist.PlaylistsScreen
import kotlinx.serialization.Serializable

/** The Playlists tab root. */
@Serializable
data object PlaylistsRoute

@Serializable
data class PlaylistRoute(val playlistId: String)

fun NavController.navigateToPlaylist(playlistId: String, navOptions: NavOptions? = null) =
    navigate(PlaylistRoute(playlistId), navOptions)

fun NavGraphBuilder.playlistsScreen(onAccountClick: () -> Unit, onPlaylistClick: (String) -> Unit) {
    composable<PlaylistsRoute> {
        PlaylistsScreen(onAccountClick = onAccountClick, onPlaylistClick = onPlaylistClick)
    }
}

fun NavGraphBuilder.playlistScreen(onBackClick: () -> Unit) {
    composable<PlaylistRoute> { entry ->
        PlaylistScreen(playlistId = entry.toRoute<PlaylistRoute>().playlistId, onBackClick = onBackClick)
    }
}
