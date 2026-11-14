package com.quantumff.segue.feature.search

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.testing.data.TestData
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
    private val openedPlaylists = mutableListOf<String>()
    private val albumActions = mutableListOf<Pair<String, MediaAction>>()
    private val artistActions = mutableListOf<Pair<String, MediaAction>>()

    private fun setContent(
        query: String = "",
        uiState: SearchUiState = SearchUiState(recentSearches = persistentListOf("abba", "blur")),
    ) {
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
            onPlaylistClick = { openedPlaylists += it },
            onAlbumAction = { album, action -> albumActions += album.id to action },
            onArtistAction = { artist, action -> artistActions += artist.id to action },
        )
        compose.setContent {
            SegueTheme {
                SearchContent(
                    query = query,
                    uiState = uiState,
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

    @Test
    fun allShowsMatchingPlaylistsAfterAlbumsAndATapOpensThePlaylist() {
        val result = SearchResult.Empty.copy(
            albums = persistentListOf(TestData.album("al1", name = "Rainbow Album")),
            playlists = persistentListOf(TestData.playlist("p1", name = "Rainy Mix")),
        )
        setContent(
            query = "rain",
            uiState = SearchUiState(results = SearchResults.Ready(result, serverPending = false, offline = false)),
        )

        val albumsTop = compose.onNodeWithText("Rainbow Album").getUnclippedBoundsInRoot().top
        val playlistsHeader = compose.onAllNodesWithText("Playlists")[1].getUnclippedBoundsInRoot().top
        assertThat(playlistsHeader).isGreaterThan(albumsTop)

        compose.onNodeWithText("Rainy Mix").performClick()

        assertThat(openedPlaylists).containsExactly("p1")
    }

    @Test
    fun allHasNoPlaylistsSectionWithoutMatchingPlaylists() {
        val result = SearchResult.Empty.copy(albums = persistentListOf(TestData.album("al1")))
        setContent(
            query = "rain",
            uiState = SearchUiState(results = SearchResults.Ready(result, serverPending = false, offline = false)),
        )

        // Only the filter chip says Playlists.
        compose.onAllNodesWithText("Playlists").assertCountEquals(1)
    }

    private fun setResults(filter: SearchFilter) = setContent(
        query = "rad",
        uiState = SearchUiState(
            filter = filter,
            results = SearchResults.Ready(
                SearchResult(
                    artists = persistentListOf(
                        TestData.artist("a1", name = "Radiohead", albumCount = 9),
                        TestData.artist("a2", name = "Radio Dept", albumCount = 1),
                    ),
                    albums = persistentListOf(TestData.album("al1", name = "Kid A")),
                    songs = persistentListOf(),
                    includesServerResults = true,
                ),
                serverPending = false,
                offline = false,
            ),
        ),
    )

    @Test
    fun artistRowsShowTheirAlbumCount() {
        setResults(SearchFilter.Artists)

        compose.onNodeWithText("9 albums").assertExists()
        compose.onNodeWithText("1 album").assertExists()
    }

    @Test
    fun anAlbumRowsMoreOptionsOpensTheAlbumMenu() {
        setResults(SearchFilter.Albums)

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Play next").performClick()

        assertThat(albumActions).containsExactly("al1" to MediaAction.PlayNext)
    }

    @Test
    fun anArtistRowsMoreOptionsOpensTheArtistMenu() {
        setResults(SearchFilter.Artists)

        compose.onAllNodesWithContentDescription("More options")[0].performClick()
        compose.onNodeWithText("Shuffle").performClick()

        assertThat(artistActions).containsExactly("a1" to MediaAction.Shuffle)
    }
}
