package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Now Playing can open on a panel or with its menu open: the player bar's Lyrics and ⋮ (DESIGN.md §5.5). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-port-xhdpi")
class NowPlayingStartTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun opensOnTheLyricsPanel() {
        setNowPlaying(NowPlayingStart(panel = NowPlayingPanel.Lyrics))

        compose.onNodeWithText(LYRIC).assertIsDisplayed()
    }

    @Test
    fun opensWithTheMenuOpen() {
        setNowPlaying(NowPlayingStart(openMenu = true))

        compose.onNodeWithText("Sleep timer").assertIsDisplayed()
        compose.onNodeWithText(LYRIC).assertDoesNotExist()
    }

    @Test
    fun opensOnTheArtByDefault() {
        setNowPlaying(NowPlayingStart.Default)

        compose.onNodeWithText(LYRIC).assertDoesNotExist()
        compose.onNodeWithText(QUEUE_BODY).assertDoesNotExist()
        compose.onNodeWithText("Sleep timer").assertDoesNotExist()
    }

    private fun setNowPlaying(start: NowPlayingStart) {
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
                    start = start,
                    posture = Posture.Flat,
                )
            }
        }
    }

    private companion object {
        const val QUEUE_BODY = "Queue body"
        const val LYRIC = "Don't get any big ideas"
    }
}
