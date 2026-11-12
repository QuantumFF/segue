package com.quantumff.segue.feature.nowplaying.sheet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.feature.nowplaying.SleepTimerChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The sleep timer sheet in a phone held sideways: too short for the presets and the custom slider. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h360dp-land")
class SleepTimerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val started = mutableListOf<SleepTimerChoice>()
    private var extended = 0
    private var dismissed = 0

    @Test
    fun `in a short window the custom slider scrolls fully into view and starts a timer`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Custom"))
        compose.onNodeWithText("Custom").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Start"))

        val screenHeight = RuntimeEnvironment.getApplication().resources.displayMetrics.heightPixels
        val slider = compose.onNode(isSlider).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertThat(slider.top).isAtLeast(0f)
        assertThat(slider.bottom).isAtMost(screenHeight.toFloat())

        compose.onNodeWithText("Start").performClick()
        assertThat(started).containsExactly(SleepTimerChoice.Minutes(20))
    }

    @Test
    fun `the title stays in view while the rows scroll`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Custom"))

        compose.onNodeWithText("Sleep timer").assertIsDisplayed()
    }

    @Test
    fun `a running timer offers +15 min, which extends it and closes the sheet`() {
        show(active = SleepTimer.AtTime(System.currentTimeMillis() + 10 * 60_000L))

        compose.onNodeWithText("+15 min").performClick()

        assertThat(extended).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `an end-of-track timer offers +15 min too`() {
        show(active = SleepTimer.EndOfTrack)

        compose.onNodeWithText("+15 min").assertIsDisplayed()
    }

    @Test
    fun `with no timer running there is no +15 min`() {
        show(active = null)

        compose.onNodeWithText("+15 min").assertDoesNotExist()
    }

    @Test
    fun `the custom slider reads its value in minutes`() {
        show()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Custom"))
        compose.onNodeWithText("Custom").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Start"))

        compose.onNode(isSlider).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 min"))
        compose.onNode(isSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        compose.onNode(isSlider).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "90 min"))
    }

    private val isSlider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun show(active: SleepTimer? = SleepTimer.EndOfAlbum) {
        compose.setContent {
            SegueTheme {
                SleepTimerSheet(
                    active = active,
                    onStart = { started += it },
                    onCancel = {},
                    onDismissRequest = { dismissed++ },
                    onExtend = { extended++ },
                )
            }
        }
    }
}
