package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
class SortChipTest {
    @get:Rule
    val compose = createComposeRule()

    private var clicks = 0

    @Test
    fun `the sort chip is a dropdown that changes the sort`() {
        show()

        compose.onNodeWithText("Recently added")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assert(
                SemanticsMatcher("click label is \"Change sort\"") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Change sort"
                },
            )
    }

    @Test
    fun `the sort chip is never announced as selected or not selected`() {
        show()

        compose.onNodeWithText("Recently added")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
    }

    @Test
    fun `tapping the sort chip opens the picker`() {
        show()

        compose.onNodeWithText("Recently added").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                SegueSortChip(label = "Recently added", onClick = { clicks++ })
            }
        }
    }
}
