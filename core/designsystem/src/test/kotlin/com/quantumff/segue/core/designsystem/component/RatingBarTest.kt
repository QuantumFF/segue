package com.quantumff.segue.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class RatingBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<Int>()
    private var rating by mutableIntStateOf(3)

    @Test
    fun `the rating reads as one adjustable control, Rating, 3 of 5 stars`() {
        show()

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertRangeInfoEquals(ProgressBarRangeInfo(3f, 0f..5f, steps = 4))
            .assert(hasContentDescription("Rating", substring = false))
            .assert(hasStateDescription("3 of 5 stars"))
        // One stop for TalkBack: the stars are not focusable on their own.
        compose.onAllNodesWithContentDescription("of 5 stars", substring = true).assertCountEquals(0)
    }

    @Test
    fun `adjusting the rating rates the album, down to no rating`() {
        show()

        val control = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        control.performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        control.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }

        assertThat(changes).containsExactly(4, 0).inOrder()
    }

    @Test
    fun `each star is 48 to touch`() {
        show()

        for (star in 1..5) {
            star(star)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `tapping a star sets that rating, and tapping the current one clears it`() {
        show()

        star(4).performClick()
        star(3).performClick()

        assertThat(changes).containsExactly(4, 0).inOrder()
    }

    /** The [n]th star, for touch only: TalkBack never sees it, so it's only in the unmerged tree. */
    private fun star(n: Int) = compose.onAllNodes(hasClickAction(), useUnmergedTree = true)[n - 1]

    private fun show() {
        compose.setContent {
            SegueTheme { RatingBar(rating = rating, onRatingChange = { changes += it }) }
        }
    }
}
