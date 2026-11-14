package com.quantumff.segue.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "See all" on Home's Playlists and Genres sections (DESIGN.md §4.1). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class HomeSeeAllTest {
    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()

    private val state = PreviewHomeState.copy(
        playlists = persistentListOf(
            TestData.playlist("p1"),
            TestData.playlist("p2"),
        ),
    )

    private fun show() {
        compose.setContent {
            SegueTheme {
                HomeScreen(
                    uiState = state,
                    onAccountClick = {},
                    onAlbumClick = {},
                    onPlaylistClick = {},
                    onGenreClick = {},
                    onSeeAllClick = { taps += "albums $it" },
                    onPlayAlbum = {},
                    onAlbumAction = { _, _ -> },
                    onRefresh = {},
                    onRefreshExplore = {},
                    onRetrySync = {},
                    onMessageDismiss = {},
                    onOfflineModeSettingClick = {},
                    onPlaylistsSeeAllClick = { taps += "playlists" },
                    onGenresSeeAllClick = { taps += "genres" },
                )
            }
        }
    }

    /** The header "See all" on the same line as the section title [title], scrolled into view. */
    private fun seeAllOf(title: String): SemanticsNodeInteraction {
        val homeList =
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        compose.onNode(homeList).performScrollToNode(hasText(title))
        val titleMiddle = compose.onNode(hasText(title)).fetchSemanticsNode().boundsInRoot.center.y
        val onTitleLine = SemanticsMatcher("on the line of $title") { node ->
            node.boundsInRoot.top <= titleMiddle && titleMiddle <= node.boundsInRoot.bottom
        }
        return compose.onNode(hasText("See all") and hasClickAction() and onTitleLine)
    }

    @Test
    fun `the Playlists section's See all opens the playlists`() {
        show()

        seeAllOf("Playlists").performClick()

        assertThat(taps).containsExactly("playlists")
    }

    @Test
    fun `the Genres section's See all opens the genres`() {
        show()

        seeAllOf("Genres").performClick()

        assertThat(taps).containsExactly("genres")
    }
}
