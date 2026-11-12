package com.quantumff.segue.feature.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRowTest {
    @get:Rule
    val compose = createComposeRule()

    private var expanded by mutableStateOf(false)

    @Test
    fun `the played row is a button announced as collapsed or expanded`() {
        show()

        compose.onNodeWithText("3 played")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()

        compose.onNodeWithText("3 played")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                HistoryRow(history = QueueListItem.History(count = 3, expanded = expanded), onClick = {
                    expanded =
                        !expanded
                })
            }
        }
    }
}
