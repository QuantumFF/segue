package com.quantumff.segue.feature.library.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.core.designsystem.component.ScreenBackground
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.feature.library.GenreScreen
import com.quantumff.segue.feature.library.LibraryScreen
import com.quantumff.segue.feature.library.PlaylistsTabSlot
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** One genre's albums. Pushed from Library > Genres, Home, Search and album genre pills. */
@Serializable
data class GenreRoute(val genre: String)

fun NavController.navigateToGenre(genre: String, navOptions: NavOptions? = null) =
    navigate(GenreRoute(genre), navOptions)

/**
 * @param requestedTab a tab to switch to once, over the last one shown (Home's "See all" on
 * Genres); [onRequestedTabShow] runs once it has switched so the request can be dropped.
 */
@Suppress("LongParameterList")
fun NavGraphBuilder.libraryScreen(
    onAccountClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    downloadsTab: (@Composable () -> Unit)? = null,
    playlistsTab: PlaylistsTabSlot? = null,
    requestedTab: () -> LibraryTab? = { null },
    onRequestedTabShow: () -> Unit = {},
) {
    composable<LibraryRoute> {
        ScreenBackground {
            LibraryScreen(
                onAccountClick = onAccountClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onGenreClick = onGenreClick,
                onDownloadsClick = onDownloadsClick,
                onAddToPlaylist = onAddToPlaylist,
                downloadsTab = downloadsTab,
                playlistsTab = playlistsTab,
                requestedTab = requestedTab(),
                onRequestedTabShow = onRequestedTabShow,
            )
        }
    }
}

/** @param onOfflineModeSettingClick the offline chip in Offline mode: Settings at Downloads & storage. */
fun NavGraphBuilder.genreScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    onOfflineModeSettingClick: () -> Unit,
) {
    composable<GenreRoute> {
        ScreenBackground {
            GenreScreen(
                onBackClick = onBackClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onAddToPlaylist = onAddToPlaylist,
                onOfflineModeSettingClick = onOfflineModeSettingClick,
            )
        }
    }
}
