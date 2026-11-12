package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val current = PreviewData.tracks.first { it.isCurrent }.copy(isPlaying = true)
    private val other = PreviewData.tracks.first { !it.isCurrent }

    @Test
    fun `the current song's row is announced as Now playing, and other rows aren't`() {
        // The playing row's equalizer animates forever, so the clock only moves when asked.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SegueTheme {
                Column {
                    TrackRow(state = current, onClick = {})
                    TrackRow(state = other, onClick = {})
                }
            }
        }

        compose.onNode(hasClickAction() and hasText(current.title))
            .assert(hasStateDescription("Now playing"))
        compose.onNode(hasClickAction() and hasText(other.title))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `a selected row playing now is announced as both selected and now playing`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SegueTheme {
                TrackRow(state = current, onClick = {}, selected = true)
            }
        }

        compose.onNode(hasClickAction() and hasText(current.title))
            .assert(hasStateDescription("Selected, now playing"))
    }

    @Test
    fun `a row without a tap action is not clickable`() {
        compose.setContent {
            SegueTheme {
                TrackRow(state = other, onClick = null)
            }
        }

        compose.onNode(hasText(other.title)).assertHasNoClickAction()
    }
}
