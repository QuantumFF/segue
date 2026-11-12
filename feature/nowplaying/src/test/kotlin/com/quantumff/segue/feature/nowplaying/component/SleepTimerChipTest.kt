package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SleepTimer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sleep timer chip on its own: ticking each minute, read out, and tappable. */
@RunWith(RobolectricTestRunner::class)
class SleepTimerChipTest {
    @get:Rule
    val compose = createComposeRule()

    private var now by mutableStateOf(START)
    private var clicks = 0

    private fun show(timer: SleepTimer) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                SleepTimerChip(timer = timer, onClick = { clicks++ }, clock = { now })
            }
        }
    }

    @Test
    fun `the minutes left go down once a minute`() {
        show(SleepTimer.AtTime(START + 23 * MINUTE))
        compose.onNodeWithText("23 min").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        now = START + MINUTE
        compose.mainClock.advanceTimeBy(MINUTE + 1_000)
        compose.waitForIdle()

        compose.onNodeWithText("22 min").assertIsDisplayed()
    }

    @Test
    fun `an end of track timer says so`() {
        show(SleepTimer.EndOfTrack)

        compose.onNodeWithText("End of track").assertIsDisplayed()
    }

    @Test
    fun `screen readers hear the timer state, and hear it change`() {
        show(SleepTimer.AtTime(START + 23 * MINUTE))

        compose.onNode(hasContentDescription("Sleep timer: 23 min"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `tapping the chip runs its action`() {
        show(SleepTimer.EndOfAlbum)

        compose.onNodeWithText("End of album").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    private companion object {
        // On a minute boundary, so the next tick is a whole minute away.
        const val START = 1_700_000_040_000L
        const val MINUTE = 60_000L
    }
}
