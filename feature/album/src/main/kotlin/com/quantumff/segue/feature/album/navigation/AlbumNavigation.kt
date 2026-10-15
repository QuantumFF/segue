package com.quantumff.segue.feature.album.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.feature.album.AlbumDetailRoute
import kotlinx.serialization.Serializable

/** [albumId] is read by [com.quantumff.segue.feature.album.AlbumViewModel] under the same key. */
@Serializable
data class AlbumRoute(val albumId: String)

fun NavController.navigateToAlbum(albumId: String, navOptions: NavOptions? = null) =
    navigate(AlbumRoute(albumId), navOptions)

fun NavGraphBuilder.albumScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    playActions: PlayActions,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    composable<AlbumRoute> {
        AlbumDetailRoute(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onGenreClick = onGenreClick,
            playActions = playActions,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
