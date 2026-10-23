package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongFilter
import com.quantumff.segue.core.model.SongSortOrder
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The filter row's sort chip and Favorites and Downloaded chips (DESIGN.md §4.2). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryFilterRowTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(LibraryUiState(albumCount = 1, artistCount = 1, songCount = 1))
    private val events = mutableListOf<LibraryEvent>()

    @Test
    fun `the top bar has no sort icon on any tab`() {
        show()

        LibraryTab.entries.forEach { tab ->
            state = state.copy(tab = tab)
            compose.onAllNodesWithContentDescription("Sort").assertCountEquals(0)
        }
    }

    @Test
    fun `the artists chips act on the Artists tab`() {
        state = state.copy(tab = LibraryTab.Artists)
        show()

        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("Downloaded").performClick()

        assertThat(events).containsExactly(
            LibraryEvent.CycleFavorites(LibraryTab.Artists),
            LibraryEvent.CycleDownloaded(LibraryTab.Artists),
        ).inOrder()
    }

    @Test
    fun `the artists sort chip offers the artist sorts`() {
        state = state.copy(tab = LibraryTab.Artists)
        show()

        compose.onNodeWithText("Name").performClick()
        compose.onNodeWithText("Album count").performClick()

        assertThat(events).containsExactly(LibraryEvent.SetArtistSort(ArtistSortOrder.AlbumCount))
    }

    @Test
    fun `the songs chips act on the Songs tab`() {
        state = state.copy(tab = LibraryTab.Songs)
        show()

        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("Downloaded").performClick()

        assertThat(events).containsExactly(
            LibraryEvent.CycleFavorites(LibraryTab.Songs),
            LibraryEvent.CycleDownloaded(LibraryTab.Songs),
        ).inOrder()
    }

    @Test
    fun `the songs sort chip offers the song sorts`() {
        state = state.copy(tab = LibraryTab.Songs)
        show()

        compose.onNodeWithText("Title").performClick()
        listOf("Artist", "Album", "Recently added", "Year").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Play count").performClick()

        assertThat(events).containsExactly(LibraryEvent.SetSongSort(SongSortOrder.PlayCount))
    }

    @Test
    fun `shuffle all stays in the songs filter row while there are songs`() {
        state = state.copy(tab = LibraryTab.Songs)
        show()

        compose.onNodeWithText("Shuffle all").performClick()
        state = state.copy(songCount = 0, songFilter = SongFilter(starred = true))
        compose.onNodeWithText("Shuffle all").assertDoesNotExist()

        assertThat(events).containsExactly(LibraryEvent.ShuffleAll)
    }

    @Test
    fun `songs filters that match nothing offer to clear them`() {
        state = state.copy(tab = LibraryTab.Songs, songCount = 0, songFilter = SongFilter(downloaded = false))
        show()

        compose.onNodeWithText("No songs match").assertExists()
        compose.onNodeWithText("Clear filters").performClick()

        assertThat(events).containsExactly(LibraryEvent.ClearFilters(LibraryTab.Songs))
    }

    @Test
    fun `the albums chips act on the Albums tab`() {
        show()

        compose.onNodeWithText("Favorites").performClick()

        assertThat(events).containsExactly(LibraryEvent.CycleFavorites(LibraryTab.Albums))
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.from(listOf(TestData.album("1")))).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.from(listOf(TestData.artist("1")))).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = { events += it },
                )
            }
        }
    }
}
