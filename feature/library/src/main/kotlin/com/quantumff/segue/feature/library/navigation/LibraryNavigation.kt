package com.quantumff.segue.feature.library.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.library.GenreScreen
import com.quantumff.segue.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** One genre's albums. Pushed from Library > Genres, Home, Search and album genre pills. */
@Serializable
data class GenreRoute(val genre: String)

fun NavController.navigateToGenre(genre: String, navOptions: NavOptions? = null) =
    navigate(GenreRoute(genre), navOptions)

@Suppress("LongParameterList")
fun NavGraphBuilder.libraryScreen(
    onAccountClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    downloadsTab: (@Composable () -> Unit)? = null,
) {
    composable<LibraryRoute> {
        LibraryScreen(
            onAccountClick = onAccountClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onGenreClick = onGenreClick,
            onDownloadsClick = onDownloadsClick,
            onAddToPlaylist = onAddToPlaylist,
            downloadsTab = downloadsTab,
        )
    }
}

fun NavGraphBuilder.genreScreen(onBackClick: () -> Unit, onAlbumClick: (String) -> Unit) {
    composable<GenreRoute> {
        GenreScreen(onBackClick = onBackClick, onAlbumClick = onAlbumClick)
    }
}
