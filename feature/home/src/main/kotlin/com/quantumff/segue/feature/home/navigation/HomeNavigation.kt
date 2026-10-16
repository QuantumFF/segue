package com.quantumff.segue.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.feature.home.AlbumListScreen
import com.quantumff.segue.feature.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/** "See all" for a Home section. [listType] is an [AlbumListType] name. */
@Serializable
data class AlbumListRoute(val listType: String)

fun NavController.navigateToAlbumList(type: AlbumListType, navOptions: NavOptions? = null) =
    navigate(AlbumListRoute(type.name), navOptions)

@Suppress("LongParameterList")
fun NavGraphBuilder.homeScreen(
    onAccountClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onSeeAllClick: (AlbumListType) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onAccountClick = onAccountClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onGenreClick = onGenreClick,
            onSeeAllClick = onSeeAllClick,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}

fun NavGraphBuilder.albumListScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    composable<AlbumListRoute> {
        AlbumListScreen(
            onBackClick = onBackClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
