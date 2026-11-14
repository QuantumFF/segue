package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each Library tab's page renders on its own, as a pager will render it. */
@RunWith(RobolectricTestRunner::class)
class LibraryTabPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = List(COUNT) { TestData.album(id = "%03d".format(it)) }
    private val songs = List(COUNT) { TestData.song(id = "%03d".format(it)) }
    private val albumPages = flowOf(PagingData.from(albums))
    private val artistPages = flowOf(PagingData.empty<Artist>())
    private val songPages = flowOf(PagingData.from(songs))

    // The selected tab is Albums throughout: a page shows the tab it is given, not the selected one.
    private val state = LibraryUiState(tab = LibraryTab.Albums, albumCount = COUNT, songCount = COUNT)

    @Test
    fun `a page shows its own tab whichever tab is selected`() {
        show(LibraryTab.Songs)

        compose.onNodeWithText(songs.first().title).assertIsDisplayed()
    }

    @Test
    fun `a page that leaves and comes back keeps its place`() {
        var shown by mutableStateOf(true)
        show(LibraryTab.Albums) { shown }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)

        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()

        compose.onNodeWithText(albums[SCROLLED_TO].name).assertIsDisplayed()
        compose.onNodeWithText(albums.first().name).assertDoesNotExist()
    }

    private fun show(tab: LibraryTab, shown: () -> Boolean = { true }) {
        compose.setContent {
            SegueTheme {
                val scroll = rememberLibraryScrollStates()
                val albumItems = albumPages.collectAsLazyPagingItems()
                val artistItems = artistPages.collectAsLazyPagingItems()
                val songItems = songPages.collectAsLazyPagingItems()
                if (shown()) {
                    LibraryTabPage(
                        tab = tab,
                        state = state,
                        albums = albumItems,
                        artists = artistItems,
                        songs = songItems,
                        scroll = scroll,
                        onEvent = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val COUNT = 100
        const val SCROLLED_TO = 60
    }
}
