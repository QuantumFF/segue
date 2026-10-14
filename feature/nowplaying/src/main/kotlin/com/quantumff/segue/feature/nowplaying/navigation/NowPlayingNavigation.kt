package com.quantumff.segue.feature.nowplaying.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.nowplaying.NowPlayingScreen
import kotlinx.serialization.Serializable

@Serializable
data object NowPlayingRoute

fun NavController.navigateToNowPlaying(navOptions: NavOptions? = null) = navigate(NowPlayingRoute, navOptions)

fun NavGraphBuilder.nowPlayingScreen() {
    composable<NowPlayingRoute> {
        NowPlayingScreen()
    }
}
