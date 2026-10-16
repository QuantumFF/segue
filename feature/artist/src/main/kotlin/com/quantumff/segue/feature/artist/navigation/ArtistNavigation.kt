package com.quantumff.segue.feature.artist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.artist.ArtistDetailRoute
import kotlinx.serialization.Serializable

/** [artistId] is read by [com.quantumff.segue.feature.artist.ArtistViewModel] under the same key. */
@Serializable
data class ArtistRoute(val artistId: String)

fun NavController.navigateToArtist(artistId: String, navOptions: NavOptions? = null) =
    navigate(ArtistRoute(artistId), navOptions)

fun NavGraphBuilder.artistScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    composable<ArtistRoute> {
        ArtistDetailRoute(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
