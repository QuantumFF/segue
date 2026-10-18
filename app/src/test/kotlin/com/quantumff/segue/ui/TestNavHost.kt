package com.quantumff.segue.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.quantumff.segue.feature.album.navigation.AlbumRoute
import com.quantumff.segue.feature.album.navigation.navigateToAlbum
import com.quantumff.segue.feature.artist.navigation.ArtistRoute
import com.quantumff.segue.feature.artist.navigation.navigateToArtist
import com.quantumff.segue.feature.home.navigation.HomeRoute
import com.quantumff.segue.feature.library.navigation.LibraryRoute
import com.quantumff.segue.feature.playlist.navigation.PlaylistsRoute
import com.quantumff.segue.feature.search.navigation.SearchRoute
import com.quantumff.segue.navigation.HomeGraph
import com.quantumff.segue.navigation.LibraryGraph
import com.quantumff.segue.navigation.PlaylistsGraph
import com.quantumff.segue.navigation.SearchGraph

/**
 * The app's graph shape (a nested graph per tab, details at the root) with plain stand-in
 * screens, so shell navigation is tested without Hilt or data.
 */
@Composable
fun TestNavHost(appState: SegueAppState, modifier: Modifier = Modifier) {
    NavHost(navController = appState.navController, startDestination = HomeGraph, modifier = modifier) {
        navigation<HomeGraph>(startDestination = HomeRoute) {
            composable<HomeRoute> {
                Screen("Home root", "Open a sample album") { appState.navigate { navigateToAlbum("sample-album") } }
            }
        }
        navigation<LibraryGraph>(startDestination = LibraryRoute) {
            composable<LibraryRoute> {
                Screen("Library root", "Open a sample artist") {
                    appState.navigate { navigateToArtist("sample-artist") }
                }
            }
        }
        navigation<PlaylistsGraph>(startDestination = PlaylistsRoute) {
            composable<PlaylistsRoute> { Screen("Playlists root", "Open a sample playlist") {} }
        }
        navigation<SearchGraph>(startDestination = SearchRoute) {
            composable<SearchRoute> { Text("Search root") }
        }
        composable<AlbumRoute> { Text("Album ${it.toRoute<AlbumRoute>().albumId}") }
        composable<ArtistRoute> { Text("Artist ${it.toRoute<ArtistRoute>().artistId}") }
    }
}

@Composable
private fun Screen(title: String, action: String, onAction: () -> Unit) {
    Column {
        Text(title)
        TextButton(onClick = onAction) { Text(action) }
    }
}
