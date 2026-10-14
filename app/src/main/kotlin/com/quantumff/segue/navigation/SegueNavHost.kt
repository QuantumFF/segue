package com.quantumff.segue.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.quantumff.segue.feature.album.navigation.albumScreen
import com.quantumff.segue.feature.artist.navigation.artistScreen
import com.quantumff.segue.feature.downloads.navigation.downloadsScreen
import com.quantumff.segue.feature.home.navigation.HomeRoute
import com.quantumff.segue.feature.home.navigation.homeScreen
import com.quantumff.segue.feature.library.navigation.libraryScreen
import com.quantumff.segue.feature.login.navigation.loginScreen
import com.quantumff.segue.feature.nowplaying.navigation.nowPlayingScreen
import com.quantumff.segue.feature.playlist.navigation.playlistScreen
import com.quantumff.segue.feature.queue.navigation.queueScreen
import com.quantumff.segue.feature.search.navigation.searchScreen
import com.quantumff.segue.feature.settings.navigation.settingsScreen

/** The only place feature graphs meet. Features navigate through callbacks wired here. */
@Composable
fun SegueNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        homeScreen()
        libraryScreen()
        albumScreen()
        artistScreen()
        playlistScreen()
        searchScreen()
        nowPlayingScreen()
        queueScreen()
        settingsScreen()
        loginScreen()
        downloadsScreen()
    }
}
