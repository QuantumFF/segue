package com.quantumff.segue.feature.nowplaying

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** A set sleep timer shows a chip in Now Playing's top bar in every layout (DESIGN.md §4.7). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h900dp-port-xhdpi")
internal class NowPlayingSleepTimerChipTest(
    @Suppress("unused") private val layout: String,
    private val widthDp: Int,
    private val heightDp: Int,
    private val posture: Posture,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aSetTimerShowsTheChip() {
        show(SleepTimer.EndOfTrack)

        compose.onNodeWithText("End of track").assertIsDisplayed()
    }

    @Test
    fun noTimerNoChip() {
        show(null)

        compose.onNodeWithText("End of track").assertDoesNotExist()
    }

    @Test
    fun tappingTheChipOpensTheSleepTimerSheet() {
        show(SleepTimer.EndOfAlbum)

        compose.onNodeWithText("End of album").performClick()

        compose.onNodeWithText("Sleep timer").assertIsDisplayed()
    }

    private fun show(timer: SleepTimer?) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                NowPlayingScreen(
                    state = PreviewState.copy(sleepTimer = timer),
                    positionMillis = { 112_000L },
                    lyrics = LyricsUiState.Loaded(LyricsContent.Unsynced(persistentListOf("Lyric"))),
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loading,
                    actions = NowPlayingActions.None,
                    queueContent = {},
                    modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp),
                    posture = posture,
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun layouts() = listOf(
            arrayOf("phone", 360, 800, Posture.Flat),
            arrayOf("phone landscape", 640, 360, Posture.Flat),
            arrayOf("tabletop", 840, 900, Posture.Tabletop(hingeTop = 450.dp, hingeBottom = 450.dp)),
            arrayOf("two panes", 840, 900, Posture.Flat),
        )
    }
}
