package com.quantumff.segue.feature.search

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SearchContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val removed = mutableListOf<String>()
    private var cleared = 0

    private fun setContent(query: String = "") {
        val callbacks = SearchCallbacks(
            onQueryChange = {},
            onSubmit = {},
            onFilterChange = {},
            onRecentSearchClick = {},
            onRemoveRecentSearch = { removed += it },
            onClearRecentSearches = { cleared++ },
            onGenreClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onSongClick = {},
            onSongAction = { _, _ -> },
            onSongSwipe = { _, _ -> },
            onMessageDismiss = {},
        )
        compose.setContent {
            SegueTheme {
                SearchContent(
                    query = query,
                    uiState = SearchUiState(recentSearches = persistentListOf("abba", "blur")),
                    callbacks = callbacks,
                    autoFocus = false,
                )
            }
        }
    }

    @Test
    fun theFieldIsLabelledWithItsHint() {
        setContent()

        compose.onNode(hasSetTextAction()).assertContentDescriptionEquals("Search")
    }

    @Test
    fun theFieldKeepsItsLabelOnceTyped() {
        setContent(query = "rad")

        compose.onNode(hasSetTextAction()).assertContentDescriptionEquals("Search")
    }

    @Test
    fun eachRecentSearchHasARemoveButtonWithA48dpTarget() {
        setContent()

        compose.onNodeWithContentDescription("Remove abba")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertThat(removed).containsExactly("abba")
    }

    @Test
    fun clearAsksBeforeRemovingEveryRecentSearch() {
        setContent()

        compose.onNodeWithText("Clear").performClick()

        compose.onNodeWithText("Clear recent searches?").assertExists()
        assertThat(cleared).isEqualTo(0)

        compose.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()

        assertThat(cleared).isEqualTo(1)
        compose.onNodeWithText("Clear recent searches?").assertDoesNotExist()
    }
}
