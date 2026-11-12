package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A genre pill is a 48dp button when it opens something, and plain text when it doesn't (DESIGN.md §5.6). */
@RunWith(RobolectricTestRunner::class)
class PillTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tappable pill is a button with a 48dp touch target`() {
        compose.setContent { SegueTheme { Pill(label = "Shoegaze", onClick = {}) } }

        compose.onNodeWithText("Shoegaze")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(SegueSizes.touchTarget)
    }

    @Test
    fun `a pill without a tap is not a button and keeps its size`() {
        compose.setContent { SegueTheme { Pill(label = "Shoegaze") } }

        compose.onNodeWithText("Shoegaze")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assertHeightIsEqualTo(SegueSizes.pill)
    }
}
