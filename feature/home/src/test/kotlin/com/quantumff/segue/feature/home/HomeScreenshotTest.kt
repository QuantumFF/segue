package com.quantumff.segue.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
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
    @Test
    fun home() = capture("home") { Home(PreviewHomeState) }

    @Test
    fun homeFirstSync() = capture("home_first_sync") {
        Home(HomeUiState(isLoading = false, syncProgress = SyncProgress.Running(SyncPhase.Albums, 1240, 5600)))
    }

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
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$theme.png") {
                SegueTheme(systemDark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }
}
