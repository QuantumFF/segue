package com.quantumff.segue.feature.artist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.quantumff.segue.feature.artist.ArtistScreen
import kotlinx.serialization.Serializable

@Serializable
data class ArtistRoute(val artistId: String)

fun NavController.navigateToArtist(artistId: String, navOptions: NavOptions? = null) =
    navigate(ArtistRoute(artistId), navOptions)

fun NavGraphBuilder.artistScreen(onBackClick: () -> Unit, onAlbumClick: (String) -> Unit) {
    composable<ArtistRoute> { entry ->
        ArtistScreen(
            artistId = entry.toRoute<ArtistRoute>().artistId,
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
        )
    }
}
