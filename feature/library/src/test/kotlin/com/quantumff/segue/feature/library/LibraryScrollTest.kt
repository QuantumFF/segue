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
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Song
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
    private var state by mutableStateOf(LibraryUiState(sort = AlbumSortOrder.Name, albumCount = ALBUM_COUNT))

    @Test
    fun `a new sort starts at the top once its albums land`() {
        showScrolledAlbums()

        // The real screen sees the new sort a few frames before the re-sorted albums arrive.
        state = state.copy(sort = AlbumSortOrder.Year)
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
        state = state.copy(sort = AlbumSortOrder.Year)
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsDisplayed()
    }

    @Test
    fun `a new filter starts the album list at the top`() {
        state = state.copy(viewMode = LibraryViewMode.List)
        showScrolledAlbums()

        state = state.copy(filter = state.filter.copy(starredOnly = true))
        compose.waitForIdle()
        pages.value = PagingData.from(resorted)
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsDisplayed()
    }

    @Test
    fun `albums landing after the user scrolls leave them where they are`() {
        showScrolledAlbums()
        state = state.copy(sort = AlbumSortOrder.Year)
        pages.value = PagingData.from(resorted)
        compose.waitForIdle()

        repeat(SWIPES) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        pages.value = PagingData.from(resorted + TestData.album(id = "new"))
        compose.waitForIdle()

        compose.onNodeWithText(resorted.first().name).assertIsNotDisplayed()
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
