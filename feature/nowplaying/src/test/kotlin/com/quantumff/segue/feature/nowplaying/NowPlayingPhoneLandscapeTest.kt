package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.nowplaying.component.NOW_PLAYING_ART_TAG
import org.junit.Assert.assertTrue
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

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun aSmallPhoneKeepsALargeCover() = assertRegions(panelOpen = false, minArt = 160.dp)

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun aSmallPhoneWithAPanelKeepsTheCoverAtItsFloor() {
        assertRegions(panelOpen = true, minArt = 160.dp)
        captureScreenRoboImage("src/test/screenshots/now_playing_phone_landscape_640_queue.png")
    }

    @Test
    @Config(qualifiers = "w684dp-h360dp-land-xhdpi")
    fun aPhoneJustWiderKeepsTheCoverWithoutAPanel() = assertRegions(panelOpen = false, minArt = 160.dp)

    @Test
    @Config(qualifiers = "w684dp-h360dp-land-xhdpi")
    fun aPhoneJustWiderKeepsTheCoverAtItsFloorWithAPanel() = assertRegions(panelOpen = true, minArt = 160.dp)

    @Test
    fun aPhoneKeepsTheCoverWithoutAPanel() = assertRegions(panelOpen = false, minArt = 160.dp)

    @Test
    fun aPhoneKeepsTheCoverWithAPanel() = assertRegions(panelOpen = true, minArt = 160.dp)

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xhdpi")
    fun aLargePhoneKeepsTheCoverWithoutAPanel() = assertRegions(panelOpen = false, minArt = 160.dp)

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xhdpi")
    fun aLargePhoneKeepsTheCoverWithAPanel() = assertRegions(panelOpen = true, minArt = 160.dp)

    /** The cover is at least [minArt] wide, and the art, controls and panel sit side by side, apart. */
    private fun assertRegions(panelOpen: Boolean, minArt: Dp) {
        setNowPlaying()
        if (panelOpen) {
            compose.onNode(hasText("Queue").or(hasContentDescription("Queue"))).performClick()
            compose.waitForIdle()
        }
        val art = compose.onNodeWithTag(NOW_PLAYING_ART_TAG).getUnclippedBoundsInRoot()
        assertTrue("the cover is ${art.width} wide, under $minArt", art.width >= minArt)
        assertTrue("the cover is square", (art.width - art.height).value in -0.5f..0.5f)
        val controlsStart = compose.onNodeWithContentDescription("Collapse").getUnclippedBoundsInRoot().left
        assertTrue("the cover ends at ${art.right}, past the controls at $controlsStart", art.right <= controlsStart)
        val controlsEnd = compose.onNodeWithContentDescription("Repeat").getUnclippedBoundsInRoot().right
        if (panelOpen) {
            val panel = compose.onNodeWithText(QUEUE_BODY).getUnclippedBoundsInRoot()
            assertTrue("the controls end at $controlsEnd, past the panel at ${panel.left}", controlsEnd <= panel.left)
        } else {
            compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
        }
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
