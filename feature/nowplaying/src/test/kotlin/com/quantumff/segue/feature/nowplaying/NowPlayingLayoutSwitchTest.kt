package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The open panel is one choice across Now Playing's layouts (DESIGN.md §4.7): unfolding a tabletop
 * foldable into two panes shows the panel that was open, and folding it back opens the tab chosen there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h900dp-port-xhdpi")
class NowPlayingLayoutSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    private var posture: Posture by mutableStateOf(TABLETOP)

    @Test
    fun thePanelOpenInTabletopIsTheTabShownInTwoPanes() {
        setNowPlaying()
        compose.onNode(hasText("Lyrics").or(hasContentDescription("Lyrics"))).performClick()
        compose.onNodeWithText(LYRIC).assertIsDisplayed()

        posture = Posture.Flat
        compose.waitForIdle()

        compose.onNode(hasText("Lyrics").and(isSelectable())).assertIsSelected()
        compose.onNodeWithText(LYRIC).assertIsDisplayed()
        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
    }

    @Test
    fun theTabChosenInTwoPanesIsThePanelOpenInTabletop() {
        posture = Posture.Flat
        setNowPlaying()
        compose.onNode(hasText("Lyrics").and(isSelectable())).performClick()
        compose.onNodeWithText(LYRIC).assertIsDisplayed()

        posture = TABLETOP
        compose.waitForIdle()

        compose.onNodeWithText(LYRIC).assertIsDisplayed()
        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
    }

    @Test
    fun aPanelChosenInTwoPanesReplacesTheOneOpenBefore() {
        setNowPlaying()
        compose.onNode(hasText("Queue").or(hasContentDescription("Queue"))).performClick()
        posture = Posture.Flat
        compose.waitForIdle()
        compose.onNode(hasText("Lyrics").and(isSelectable())).performClick()
        compose.waitForIdle()

        posture = TABLETOP
        compose.waitForIdle()

        compose.onNodeWithText(LYRIC).assertIsDisplayed()
        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
    }

    @Test
    fun twoPanesLeftUntouchedOpenNoPanelInTabletop() {
        posture = Posture.Flat
        setNowPlaying()
        compose.onNodeWithText(QUEUE_BODY).assertIsDisplayed()

        posture = TABLETOP
        compose.waitForIdle()

        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
        compose.onNodeWithText(LYRIC).assertDoesNotExist()
    }

    private fun setNowPlaying() {
        compose.setContent {
            SegueTheme(systemDark = true) {
                NowPlayingScreen(
                    state = PreviewState,
                    positionMillis = { 112_000L },
                    lyrics = LyricsUiState.Loaded(LyricsContent.Unsynced(persistentListOf(LYRIC))),
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loading,
                    actions = NowPlayingActions.None,
                    queueContent = { Text(QUEUE_BODY, color = MaterialTheme.colorScheme.onSurface) },
                    posture = posture,
                )
            }
        }
    }

    private companion object {
        const val QUEUE_BODY = "Queue body"
        const val LYRIC = "Don't get any big ideas"
        val TABLETOP = Posture.Tabletop(hingeTop = 450.dp, hingeBottom = 450.dp)
    }
}
