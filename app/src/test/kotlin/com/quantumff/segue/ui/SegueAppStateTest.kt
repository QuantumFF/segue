package com.quantumff.segue.ui

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.feature.nowplaying.NowPlayingPanel
import com.quantumff.segue.feature.nowplaying.NowPlayingStart
import com.quantumff.segue.navigation.TopLevelDestination
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SegueAppStateTest {
    private val appState = SegueAppState(
        navController = NavHostController(ApplicationProvider.getApplicationContext()),
        shellLayout = ShellLayout.Expanded,
        currentTabState = mutableStateOf(TopLevelDestination.Home),
        nowPlayingOpenState = mutableStateOf(false),
        nowPlayingOpeningState = mutableIntStateOf(0),
    )

    @Test
    fun `each opening of Now Playing counts, one right after closing too`() {
        appState.openNowPlaying(NowPlayingStart(panel = NowPlayingPanel.Lyrics))
        val first = appState.nowPlayingOpening

        appState.closeNowPlaying()
        appState.openNowPlaying(NowPlayingStart(openMenu = true))

        assertThat(appState.nowPlayingOpening).isNotEqualTo(first)
        assertThat(appState.nowPlayingStart).isEqualTo(NowPlayingStart(openMenu = true))
    }

    @Test
    fun `opening Now Playing while it is open changes nothing`() {
        appState.openNowPlaying(NowPlayingStart(panel = NowPlayingPanel.Lyrics))
        val opening = appState.nowPlayingOpening

        appState.openNowPlaying()

        assertThat(appState.nowPlayingOpening).isEqualTo(opening)
        assertThat(appState.nowPlayingStart).isEqualTo(NowPlayingStart(panel = NowPlayingPanel.Lyrics))
    }
}
