package com.quantumff.segue.feature.album.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.quantumff.segue.feature.album.AlbumScreen
import kotlinx.serialization.Serializable

@Serializable
data class AlbumRoute(val albumId: String)

fun NavController.navigateToAlbum(albumId: String, navOptions: NavOptions? = null) =
    navigate(AlbumRoute(albumId), navOptions)

fun NavGraphBuilder.albumScreen() {
    composable<AlbumRoute> { entry ->
        AlbumScreen(albumId = entry.toRoute<AlbumRoute>().albumId)
    }
}
