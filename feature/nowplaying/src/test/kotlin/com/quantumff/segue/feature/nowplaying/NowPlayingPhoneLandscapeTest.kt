package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Now Playing on a phone held sideways (DESIGN.md §4.7): a large cover beside the metadata and
 * controls, and the open panel beside both. Record with `./gradlew :feature:nowplaying:recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-land-xhdpi")
class NowPlayingPhoneLandscapeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theCoverSitsBesideTheControls() {
        setNowPlaying()
        compose.onNodeWithText("Nude").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
        captureScreenRoboImage("src/test/screenshots/now_playing_phone_landscape.png")
    }

    @Test
    fun anOpenPanelSitsBesideTheArtAndControls() {
        setNowPlaying()
        compose.onNode(hasText("Queue").or(hasContentDescription("Queue"))).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(QUEUE_BODY).assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithText("Radiohead · In Rainbows", substring = true).assertIsDisplayed()
        captureScreenRoboImage("src/test/screenshots/now_playing_phone_landscape_queue.png")
    }

    private fun setNowPlaying() {
        compose.setContent {
            SegueTheme(systemDark = true) {
                NowPlayingScreen(
                    state = PreviewState,
                    positionMillis = { 112_000L },
                    lyrics = LyricsUiState.Loading,
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loading,
                    actions = NowPlayingActions.None,
                    queueContent = { Text(QUEUE_BODY, color = MaterialTheme.colorScheme.onSurface) },
                )
            }
        }
    }

    private companion object {
        const val QUEUE_BODY = "Queue body"
    }
}
