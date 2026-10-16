package com.quantumff.segue.feature.downloads.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.feature.downloads.DownloadsScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data object DownloadsRoute

fun NavController.navigateToDownloads(navOptions: NavOptions? = null) = navigate(DownloadsRoute, navOptions)

/** [onOpen] opens a pinned album, playlist or artist page. */
fun NavGraphBuilder.downloadsScreen(onBackClick: () -> Unit, onOpen: (PinKind, String) -> Unit) {
    composable<DownloadsRoute> {
        DownloadsScreenRoute(onBackClick = onBackClick, onOpen = onOpen)
    }
}
