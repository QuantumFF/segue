package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistFilter
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongFilter
import com.quantumff.segue.core.model.SongSortOrder
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = List(ALBUM_COUNT) { TestData.album(id = "%03d".format(it)) }
    private val resorted = albums.reversed()
    private val pages = MutableStateFlow(PagingData.from(albums))
    private val artists = List(ALBUM_COUNT) { TestData.artist(id = "%03d".format(it)) }
    private val artistPages = MutableStateFlow(PagingData.from(artists))
    private val songs = List(ALBUM_COUNT) { TestData.song(id = "%03d".format(it)) }
    private val songPages = MutableStateFlow(PagingData.from(songs))
    private var state by mutableStateOf(LibraryUiState(albumSort = AlbumSortOrder.Name, albumCount = ALBUM_COUNT))

    @Test
    fun `a new sort starts at the top once its albums land`() {
        showScrolledAlbums()

        // The real screen sees the new sort a few frames before the re-sorted albums arrive.
        state = state.copy(albumSort = AlbumSortOrder.Year)
        compose.waitForIdle()
        pages.value = PagingData.from(resorted)
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsDisplayed()
    }

    @Test
    fun `a new sort starts at the top when its albums land first`() {
        showScrolledAlbums()

        pages.value = PagingData.from(resorted)
        compose.waitForIdle()
        state = state.copy(albumSort = AlbumSortOrder.Year)
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsDisplayed()
    }

    @Test
    fun `a new filter starts the album list at the top`() {
        state = state.copy(viewMode = LibraryViewMode.List)
        showScrolledAlbums()

        state = state.copy(albumFilter = state.albumFilter.copy(starred = true))
        compose.waitForIdle()
        pages.value = PagingData.from(resorted)
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsDisplayed()
    }

    @Test
    fun `albums landing after the user scrolls leave them where they are`() {
        showScrolledAlbums()
        state = state.copy(albumSort = AlbumSortOrder.Year)
        pages.value = PagingData.from(resorted)
        compose.waitForIdle()

        repeat(SWIPES) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        pages.value = PagingData.from(resorted + TestData.album(id = "new"))
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsNotDisplayed()
    }

    @Test
    fun `a new artist sort starts the artist grid at the top`() {
        state = state.copy(tab = LibraryTab.Artists, artistCount = ALBUM_COUNT)
        showScrolledArtists()

        state = state.copy(artistSort = ArtistSortOrder.AlbumCount)
        compose.waitForIdle()
        artistPages.value = PagingData.from(artists.reversed())
        compose.waitForIdle()

        compose.onNodeWithText(artists.last().name).assertIsDisplayed()
    }

    @Test
    fun `a new artist filter starts the artist list at the top`() {
        state = state.copy(tab = LibraryTab.Artists, viewMode = LibraryViewMode.List, artistCount = ALBUM_COUNT)
        showScrolledArtists()

        state = state.copy(artistFilter = ArtistFilter(downloaded = true))
        compose.waitForIdle()
        artistPages.value = PagingData.from(artists.reversed())
        compose.waitForIdle()

        compose.onNodeWithText(artists.last().name).assertIsDisplayed()
    }

    @Test
    fun `a new song sort starts the song list at the top`() {
        state = state.copy(tab = LibraryTab.Songs, songCount = ALBUM_COUNT)
        showScrolledSongs()

        state = state.copy(songSort = SongSortOrder.PlayCount)
        compose.waitForIdle()
        songPages.value = PagingData.from(songs.reversed())
        compose.waitForIdle()

        compose.onNodeWithText(songs.last().title).assertIsDisplayed()
    }

    @Test
    fun `a new song filter starts the song list at the top`() {
        state = state.copy(tab = LibraryTab.Songs, songCount = ALBUM_COUNT)
        showScrolledSongs()

        state = state.copy(songFilter = SongFilter(starred = false))
        compose.waitForIdle()
        songPages.value = PagingData.from(songs.reversed())
        compose.waitForIdle()

        compose.onNodeWithText(songs.last().title).assertIsDisplayed()
    }

    private fun showScrolledSongs() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = songPages.collectAsLazyPagingItems(),
                    onEvent = {},
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)
    }

    private fun showScrolledArtists() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                    artists = artistPages.collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = {},
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)
    }

    private fun showScrolledAlbums() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = pages.collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = {},
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)
    }

    private companion object {
        const val ALBUM_COUNT = 100
        const val SCROLLED_TO = 60
        const val SWIPES = 3
    }
}
