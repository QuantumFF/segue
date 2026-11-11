package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Now Playing on a foldable in tabletop posture (DESIGN.md §3, §4.7): the art above the hinge, the
 * controls and bottom row below it. Record with `./gradlew :feature:nowplaying:recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w700dp-h840dp-port-xhdpi")
class NowPlayingTabletopTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theArtSitsAboveTheHingeAndTheControlsBelowIt() {
        setNowPlaying()
        assertAbove(compose.onNodeWithContentDescription("Collapse"))
        assertBelow(compose.onNodeWithText("Nude"))
        assertBelow(compose.onNodeWithContentDescription("Pause"))
        assertBelow(compose.onNode(hasText("Queue").or(hasContentDescription("Queue"))))
        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
        captureScreenRoboImage("src/test/screenshots/now_playing_tabletop.png")
    }

    @Test
    fun anOpenPanelTakesTheArtsPlaceAboveTheHinge() {
        setNowPlaying()
        compose.onNode(hasText("Queue").or(hasContentDescription("Queue"))).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(QUEUE_BODY).assertIsDisplayed()
        assertAbove(compose.onNodeWithText(QUEUE_BODY))
        assertBelow(compose.onNodeWithContentDescription("Pause"))
        captureScreenRoboImage("src/test/screenshots/now_playing_tabletop_queue.png")
    }

    private fun assertAbove(node: SemanticsNodeInteraction) {
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("$bounds should end above the hinge at $HINGE", bounds.bottom <= HINGE)
    }

    private fun assertBelow(node: SemanticsNodeInteraction) {
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("$bounds should start below the hinge at $HINGE", bounds.top >= HINGE)
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
                    posture = Posture.Tabletop(hingeTop = HINGE, hingeBottom = HINGE),
                )
            }
        }
    }

    private companion object {
        const val QUEUE_BODY = "Queue body"
        val HINGE = 420.dp
    }
}
