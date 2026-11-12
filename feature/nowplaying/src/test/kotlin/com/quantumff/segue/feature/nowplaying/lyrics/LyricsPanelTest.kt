package com.quantumff.segue.feature.nowplaying.lyrics

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.testing.compose.hasClickLabel
import com.quantumff.segue.feature.nowplaying.LyricsUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val seeks = mutableListOf<Long>()

    @Test
    fun `a synced line's action is Seek here, which seeks to the line`() {
        show(LyricsContent.Synced(persistentListOf("First line", "Second line"), persistentListOf(0L, 12_000L), 0L))

        compose.onNodeWithText("Second line")
            .assert(
                hasClickLabel("Seek here"),
            )
            .performClick()

        assertThat(seeks).containsExactly(12_000L)
    }

    @Test
    fun `unsynced lines do not seek`() {
        show(LyricsContent.Unsynced(persistentListOf("First line", "Second line")))

        compose.onNodeWithText("Second line").assert(hasClickAction().not())
    }

    private fun show(content: LyricsContent) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                LyricsPanel(
                    state = LyricsUiState.Loaded(content),
                    activeLine = { 0 },
                    onSeek = { seeks += it },
                    onRetry = {},
                )
            }
        }
    }
}
