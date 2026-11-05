package com.quantumff.segue.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `./gradlew :feature:home:recordRoborazziDebug`, check with `verifyRoborazziDebug`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun home() = capture("home") { Home(PreviewHomeState) }

    @Test
    fun homeFirstSync() = capture("home_first_sync") {
        Home(HomeUiState(isLoading = false, syncProgress = SyncProgress.Running(SyncPhase.Albums, 1240, 5600)))
    }

    /**
     * First open with nothing in Room, the first refresh still running. The skeleton pulses
     * forever, so the clock is paused and moved just past the 150ms delay for a stable frame.
     */
    @Test
    fun homeSkeleton() {
        var dark by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Themed(dark) { Home(HomeUiState(isLoading = false, isRefreshing = true)) }
        }
        listOf(true, false).forEach { isDark ->
            dark = isDark
            compose.mainClock.advanceTimeBy(SKELETON_SHOWN_MS)
            compose.onRoot().captureRoboImage(path("home_skeleton", isDark))
        }
    }

    /** Nothing in Room and the server failed: an error with Retry, not the empty state. */
    @Test
    fun homeError() = capture("home_error") { Home(HomeUiState(isLoading = false, refreshFailed = true)) }

    /** Offline with nothing downloaded: the chip over an offline error. */
    @Test
    fun homeErrorOffline() = capture("home_error_offline") {
        Home(HomeUiState(isLoading = false, offlineStatus = OfflineStatus.ShowingDownloads))
    }

    /** A library that really is empty. */
    @Test
    fun homeEmpty() = capture("home_empty") { Home(HomeUiState(isLoading = false)) }

    @Composable
    private fun Home(state: HomeUiState) {
        HomeScreen(
            uiState = state,
            onAccountClick = {},
            onAlbumClick = {},
            onPlaylistClick = {},
            onGenreClick = {},
            onSeeAllClick = {},
            onPlayAlbum = {},
            onAlbumAction = { _, _ -> },
            onRefresh = {},
            onRefreshExplore = {},
            onRetrySync = {},
            onMessageDismiss = {},
            onOfflineModeSettingClick = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            captureRoboImage(path(name, dark)) { Themed(dark, content) }
        }
    }

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        SegueTheme(systemDark = dark) {
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
    }

    private fun path(name: String, dark: Boolean) = "src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png"

    private companion object {
        /** Past the skeletons' 150ms delay. */
        const val SKELETON_SHOWN_MS = 200L
    }
}
