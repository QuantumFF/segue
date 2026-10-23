package com.quantumff.segue.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistFilter
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SyncProgress
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryTabStateTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<LibraryEvent>()

    @Test
    fun `a failed sync with no data shows an error whose Retry requests a sync`() {
        show(emptyLibrary.copy(syncProgress = SyncProgress.Failed("Connection refused")))

        compose.onNodeWithText("Couldn't load your library").assertIsDisplayed()
        compose.onNodeWithText("Your library is empty").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()

        assertThat(events).containsExactly(LibraryEvent.Refresh)
    }

    @Test
    fun `each tab's empty state names what the tab holds`() {
        val copy = mapOf(
            LibraryTab.Albums to "No albums yet",
            LibraryTab.Artists to "No artists yet",
            LibraryTab.Songs to "No songs yet",
            LibraryTab.Genres to "No genres yet",
        )
        var state by mutableStateOf(emptyLibrary)
        compose.setContent { Content(state) }

        for ((tab, title) in copy) {
            state = emptyLibrary.copy(tab = tab)
            compose.onNodeWithText(title).assertIsDisplayed()
        }
        state = emptyLibrary.copy(tab = LibraryTab.Artists)
        compose.onNodeWithText("Albums appear here", substring = true).assertDoesNotExist()
    }

    @Test
    fun `filters that match no album still offer to clear them`() {
        show(emptyLibrary.copy(albumFilter = AlbumFilter(starred = true), syncProgress = SyncProgress.Failed(null)))

        compose.onNodeWithText("No albums match").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()

        assertThat(events).containsExactly(LibraryEvent.ClearFilters(LibraryTab.Albums))
    }

    @Test
    fun `filters that match no artist still offer to clear them`() {
        show(
            emptyLibrary.copy(
                tab = LibraryTab.Artists,
                artistFilter = ArtistFilter(downloaded = false),
                syncProgress = SyncProgress.Failed(null),
            ),
        )

        compose.onNodeWithText("No artists match").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()

        assertThat(events).containsExactly(LibraryEvent.ClearFilters(LibraryTab.Artists))
    }

    @Test
    fun `offline with nothing downloaded says so rather than an error`() {
        show(emptyLibrary.copy(isOffline = true, syncProgress = SyncProgress.Failed(null)))

        compose.onNodeWithText("You're offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    private fun show(state: LibraryUiState) {
        compose.setContent { Content(state) }
    }

    @Composable
    private fun Content(state: LibraryUiState) {
        SegueTheme {
            LibraryContent(
                state = state,
                albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                onEvent = { events += it },
            )
        }
    }

    private companion object {
        val emptyLibrary = LibraryUiState(
            albumCount = 0,
            artistCount = 0,
            songCount = 0,
            syncProgress = SyncProgress.Succeeded,
        )
    }
}
