package com.quantumff.segue.feature.nowplaying

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceBanner
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceState
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What opens or sits inside Now Playing, readable in every theme: a Related row's ⋮ sheet in the
 * app's own colors, and the Queue panel's Continue-from-device banner on an on-art container.
 * Record with `./gradlew :feature:nowplaying:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NowPlayingOnArtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun relatedSheetLight() = relatedSheet(dark = false)

    @Test
    fun relatedSheetDark() = relatedSheet(dark = true)

    @Test
    fun queueContinueBannerLight() {
        setNowPlaying(dark = false)
        openPanel("Queue")
        captureScreenRoboImage("src/test/screenshots/now_playing_queue_continue_banner_light.png")
    }

    private fun relatedSheet(dark: Boolean) {
        setNowPlaying(dark)
        openPanel("Related")
        // The top bar's ⋮ comes first; the rest are the related rows'.
        compose.onAllNodesWithContentDescription("More options")[1].performClick()
        compose.waitForIdle()
        val theme = if (dark) "dark" else "light"
        captureScreenRoboImage("src/test/screenshots/now_playing_related_sheet_$theme.png")
    }

    private fun openPanel(label: String) {
        compose.onNode(hasText(label).or(hasContentDescription(label))).performClick()
        compose.waitForIdle()
    }

    private fun setNowPlaying(dark: Boolean) {
        compose.setContent {
            SegueTheme(systemDark = dark) {
                NowPlayingScreen(
                    state = PreviewState,
                    positionMillis = { 112_000L },
                    lyrics = LyricsUiState.Loading,
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loaded(RelatedSongs),
                    actions = NowPlayingActions.None,
                    queueContent = { ContinueBannerQueue() },
                )
            }
        }
    }

    @Composable
    private fun ContinueBannerQueue() {
        Column(Modifier.padding(SegueSpacing.lg)) {
            ContinueFromDeviceBanner(
                state = ContinueFromDeviceState(deviceName = "Feishin", detail = "Nude · 2:31 · 5 min ago"),
                onContinueClick = {},
                onDismissClick = {},
            )
        }
    }

    private companion object {
        val RelatedSongs = persistentListOf(
            Song(
                id = "r-1",
                title = "Pyramid Song",
                albumId = "al-2",
                album = "Amnesiac",
                artist = "Radiohead",
                artistId = "ar-1",
            ),
            Song(
                id = "r-2",
                title = "Teardrop",
                albumId = "al-3",
                album = "Mezzanine",
                artist = "Massive Attack",
                artistId = "ar-2",
            ),
            Song(
                id = "r-3",
                title = "Roads",
                albumId = "al-4",
                album = "Dummy",
                artist = "Portishead",
                artistId = "ar-3",
            ),
        )
    }
}
