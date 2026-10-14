package com.quantumff.segue.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(onAccountClick: () -> Unit, onAlbumClick: (String) -> Unit) {
    composable<HomeRoute> {
        HomeScreen(onAccountClick = onAccountClick, onAlbumClick = onAlbumClick)
    }
}
