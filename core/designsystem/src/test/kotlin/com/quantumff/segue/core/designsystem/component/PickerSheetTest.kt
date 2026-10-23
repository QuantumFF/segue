package com.quantumff.segue.core.designsystem.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PickerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val picked = mutableListOf<String>()
    private var dismissals = 0

    @Test
    fun `the title is a heading`() {
        show()

        compose.onNodeWithText("Sort").assert(isHeading())
    }

    @Test
    fun `rows are 48dp radio buttons in one group, and the current option is selected`() {
        show()

        compose.onNode(radioRow("Name"))
            .assertHeightIsEqualTo(48.dp)
            .assertIsSelected()
            .assert(hasParent(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        compose.onNode(radioRow("Year")).assertIsNotSelected()
    }

    @Test
    fun `picking an option reports it and dismisses the sheet`() {
        show()

        compose.onNodeWithText("Year").performClick()

        assertThat(picked).containsExactly("Year")
        assertThat(dismissals).isEqualTo(1)
    }

    @Test
    fun `a long list scrolls to its last option`() {
        val genres = List(GENRE_COUNT) { "Genre $it" }.toImmutableList()
        show(genres)

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Genre ${GENRE_COUNT - 1}"))
        compose.onNodeWithText("Genre ${GENRE_COUNT - 1}").assertIsDisplayed().performClick()

        assertThat(picked).containsExactly("Genre ${GENRE_COUNT - 1}")
    }

    private fun radioRow(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun show(options: ImmutableList<String> = persistentListOf("Name", "Year", "Recently added")) {
        compose.setContent {
            SegueTheme {
                PickerSheet(
                    title = "Sort",
                    options = options,
                    selected = options.first(),
                    label = { it },
                    onSelect = { picked += it },
                    onDismissRequest = { dismissals++ },
                )
            }
        }
    }

    private companion object {
        const val GENRE_COUNT = 60
    }
}
