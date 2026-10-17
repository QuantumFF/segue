package com.quantumff.segue.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavGraphBuilder.searchScreen(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onGenreClick = onGenreClick,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
