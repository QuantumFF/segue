package com.quantumff.segue.feature.playlist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.quantumff.segue.feature.playlist.PlaylistScreen
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistRoute(val playlistId: String)

fun NavController.navigateToPlaylist(playlistId: String, navOptions: NavOptions? = null) =
    navigate(PlaylistRoute(playlistId), navOptions)

fun NavGraphBuilder.playlistScreen() {
    composable<PlaylistRoute> { entry ->
        PlaylistScreen(playlistId = entry.toRoute<PlaylistRoute>().playlistId)
    }
}
