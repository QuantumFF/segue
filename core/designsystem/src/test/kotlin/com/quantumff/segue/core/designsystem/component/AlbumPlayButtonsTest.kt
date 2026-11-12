package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.QueuePlacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Play and Shuffle albums chevrons are 48dp touch targets on their 40dp visuals. */
@RunWith(RobolectricTestRunner::class)
class AlbumPlayButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    private val placements = mutableListOf<QueuePlacement>()

    @Test
    fun `each queue options chevron is at least 48dp`() {
        show()

        listOf(PLAY_CHEVRON, SHUFFLE_CHEVRON).forEach { chevron ->
            compose.onNodeWithContentDescription(chevron)
                .assertWidthIsAtLeast(SegueSizes.touchTarget)
                .assertHeightIsAtLeast(SegueSizes.touchTarget)
        }
    }

    @Test
    fun `the chevrons keep their 40dp visual, so the pills don't grow`() {
        show()

        val buttons = compose.onNodeWithTag(BUTTONS).getBoundsInRoot()
        val play = compose.onNodeWithContentDescription("Play albums").getBoundsInRoot()
        val shuffle = compose.onNodeWithContentDescription("Shuffle albums").getBoundsInRoot()
        // Each pill: its play half, a hairline divider and a 40 chevron; 8 between the pills.
        val expected = play.width + 1.dp + 40.dp + 8.dp + shuffle.width + 1.dp + 40.dp
        assertThat(buttons.width).isEqualTo(expected)
    }

    @Test
    fun `a press just before the chevron's visual opens the queue options`() {
        show()
        val play = compose.onNodeWithContentDescription("Play albums").getBoundsInRoot()

        // 2dp before the chevron's visual (past the hairline divider): inside the chevron's 48 target.
        compose.onNodeWithTag(BUTTONS).performTouchInput { click(Offset((play.right - 1.dp).toPx(), center.y)) }
        compose.onNodeWithText("Play next").assertExists()
        assertThat(placements).isEmpty()
    }

    @Test
    fun `a press in the middle of the play half plays`() {
        show()
        val play = compose.onNodeWithContentDescription("Play albums").getBoundsInRoot()

        compose.onNodeWithTag(BUTTONS).performTouchInput { click(Offset((play.right / 2).toPx(), center.y)) }

        assertThat(placements).containsExactly(QueuePlacement.Replace)
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                AlbumPlayButtons(onPlay = { _, placement -> placements += placement }, Modifier.testTag(BUTTONS))
            }
        }
    }

    private companion object {
        const val BUTTONS = "buttons"
        const val PLAY_CHEVRON = "Play albums: queue options"
        const val SHUFFLE_CHEVRON = "Shuffle albums: queue options"
    }
}
