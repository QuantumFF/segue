package com.quantumff.segue.feature.library

import androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The filter row fits any width (DESIGN.md §4.2): its chips scroll, and the item count keeps its own slot. */
@RunWith(RobolectricTestRunner::class)
class LibraryFilterRowWidthTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `on a small phone every albums chip scrolls into view clear of the count`() {
        show()
        assertThat(chipScrollRange()).isGreaterThan(0f)

        listOf("Recently added", "Favorites", "Downloaded", "Year", "Genre").forEach { chip ->
            compose.onNodeWithText(chip).performScrollTo().assertIsDisplayed()
            assertThat(compose.onNodeWithText(chip).right()).isAtMost(count().left())
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `on a wide screen the albums chips fit without scrolling`() {
        show()

        assertThat(chipScrollRange()).isEqualTo(0f)
        compose.onNodeWithText("Genre").assertIsDisplayed()
        assertThat(compose.onNodeWithText("Genre").right()).isAtMost(count().left())
    }

    private fun count() = compose.onNodeWithText("1,234 albums")

    /** How far the chips scroll, in pixels: 0 when they all fit. */
    private fun chipScrollRange(): Float = compose
        .onNode(hasAnyDescendant(hasText("Favorites")) and SemanticsMatcher.keyIsDefined(HorizontalScrollAxisRange))
        .fetchSemanticsNode()
        .config[HorizontalScrollAxisRange]
        .maxValue()

    private fun SemanticsNodeInteraction.right(): Dp = getUnclippedBoundsInRoot().right

    private fun SemanticsNodeInteraction.left(): Dp = getUnclippedBoundsInRoot().left

    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = LibraryUiState(albumCount = 1234),
                    albums = flowOf(PagingData.from(listOf(TestData.album("1")))).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = {},
                )
            }
        }
    }
}
