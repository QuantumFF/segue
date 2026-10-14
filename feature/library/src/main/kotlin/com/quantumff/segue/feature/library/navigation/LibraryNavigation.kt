package com.quantumff.segue.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen(
    onAccountClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
) {
    composable<LibraryRoute> {
        LibraryScreen(
            onAccountClick = onAccountClick,
            onArtistClick = onArtistClick,
            onDownloadsClick = onDownloadsClick,
        )
    }
}
