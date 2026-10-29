package com.quantumff.segue.feature.nowplaying

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Now Playing with an empty queue: only Collapse is in the top bar. */
@RunWith(RobolectricTestRunner::class)
class NothingPlayingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `nothing playing has collapse but no more options`() {
        compose.setContent {
            SegueTheme {
                NowPlayingScreen(
                    state = NowPlayingUiState(),
                    positionMillis = { 0L },
                    lyrics = LyricsUiState.Loading,
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loading,
                    actions = NowPlayingActions.None,
                    queueContent = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Collapse").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
    }
}
