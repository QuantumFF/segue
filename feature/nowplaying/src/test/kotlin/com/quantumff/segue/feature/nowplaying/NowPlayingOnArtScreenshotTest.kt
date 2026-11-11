package com.quantumff.segue.feature.nowplaying

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceBanner
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceState
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What opens or sits inside Now Playing, readable in every theme: a Related row's ⋮ sheet in the
 * app's own colors, and the Queue panel's Continue-from-device banner and the lyrics Sync chip on
 * opaque art containers, so the lines under the chip don't show through.
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
    fun queueContinueBannerLight() = continueBanner(dark = false)

    @Test
    fun queueContinueBannerDark() = continueBanner(dark = true)

    @Test
    fun lyricsSyncChipLight() = syncChip(dark = false)

    @Test
    fun lyricsSyncChipDark() = syncChip(dark = true)

    private fun continueBanner(dark: Boolean) {
        setNowPlaying(dark)
        openPanel("Queue")
        val theme = if (dark) "dark" else "light"
        captureScreenRoboImage("src/test/screenshots/now_playing_queue_continue_banner_$theme.png")
    }

    /** The Sync chip shows once the lyrics are scrolled by hand, over the lines beneath it. */
    private fun syncChip(dark: Boolean) {
        setNowPlaying(dark)
        openPanel("Lyrics")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(SyncedLyrics.lines[2]).performTouchInput { swipeUp(durationMillis = 300) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Sync").assertIsDisplayed()
        val theme = if (dark) "dark" else "light"
        captureScreenRoboImage("src/test/screenshots/now_playing_lyrics_sync_chip_$theme.png")
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
                    lyrics = LyricsUiState.Loaded(SyncedLyrics),
                    activeLyricLine = { 2 },
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
        val SyncedLyrics = LyricsContent.Synced(
            lines = List(16) { "Don't get any big ideas, they're not gonna happen, line ${it + 1}" }.toImmutableList(),
            starts = List(16) { it * 4_000L }.toImmutableList(),
            offsetMillis = 0,
        )

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
