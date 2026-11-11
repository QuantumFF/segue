package com.quantumff.segue.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A context menu in a phone held sideways: too short for every row. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h360dp-land")
class ContextMenuSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicked = mutableListOf<String>()

    @Test
    fun `in a short window the last item scrolls into view and can be clicked`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(LAST))
        compose.onNodeWithText(LAST).assertIsDisplayed().performClick()

        assertThat(clicked).containsExactly(LAST)
    }

    @Test
    fun `the header stays in view while the rows scroll`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(LAST))

        compose.onNodeWithText(TITLE).assertIsDisplayed()
    }

    private fun show() {
        val items = List(ITEM_COUNT - 1) {
            ContextMenuItem(id = "item$it", label = "Item $it", icon = SegueIcons.Music)
        }
            .plus(ContextMenuItem(id = LAST, label = LAST, icon = SegueIcons.Remove, destructive = true))
            .toImmutableList()
        compose.setContent {
            SegueTheme {
                ContextMenuSheet(
                    header = ContextMenuHeader(title = TITLE, subtitle = "Radiohead", coverArtId = null),
                    items = items,
                    onItemClick = { clicked += it.label },
                    onDismissRequest = {},
                )
            }
        }
    }

    private companion object {
        const val TITLE = "In Rainbows"
        const val LAST = "Clear queue"
        const val ITEM_COUNT = 10
    }
}
