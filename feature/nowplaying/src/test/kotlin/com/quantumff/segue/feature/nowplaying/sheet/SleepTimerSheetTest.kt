package com.quantumff.segue.feature.nowplaying.sheet

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

    private val isSlider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun show() {
        compose.setContent {
            SegueTheme {
                SleepTimerSheet(
                    active = SleepTimer.EndOfAlbum,
                    onStart = { started += it },
                    onCancel = {},
                    onDismissRequest = {},
                )
            }
        }
    }
}
