package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val swiped = mutableListOf<String>()

    @Test
    fun `swiping right plays next and swiping left adds to the queue`() {
        show()

        compose.onNodeWithTag(ROW).performTouchInput { swipeRight() }
        compose.onNodeWithTag(ROW).performTouchInput { swipeLeft() }

        assertThat(swiped).containsExactly("next", "queue").inOrder()
    }

    @Test
    fun `a short swipe springs back without acting`() {
        show()

        compose.onNodeWithTag(ROW).performTouchInput { swipeRight(startX = centerX, endX = centerX + width * 0.2f) }

        assertThat(swiped).isEmpty()
    }

    @Test
    fun `a disabled row ignores swipes and offers no actions`() {
        show(enabled = false)

        compose.onNodeWithTag(ROW).performTouchInput { swipeRight() }

        assertThat(swiped).isEmpty()
        compose.onNodeWithTag(ROW).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }

    @Test
    fun `accessibility services get both actions`() {
        show()

        compose.onNodeWithTag(ROW).assert(
            SemanticsMatcher("Play next and Add to queue") { node ->
                node.config[SemanticsActions.CustomActions].map { it.label } == listOf("Play next", "Add to queue")
            },
        )
    }

    private fun show(enabled: Boolean = true) {
        compose.setContent {
            SegueTheme {
                TrackSwipeBox(
                    onPlayNext = { swiped += "next" },
                    onAddToQueue = { swiped += "queue" },
                    enabled = enabled,
                    modifier = Modifier.testTag(ROW),
                ) {
                    Text("Song", Modifier.fillMaxWidth().height(56.dp))
                }
            }
        }
    }

    private companion object {
        const val ROW = "row"
    }
}
