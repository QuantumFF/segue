package com.quantumff.segue.feature.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall enough for a whole artist menu.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryArtistRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val artists = List(2) { TestData.artist(id = "$it") }
    private val events = mutableListOf<LibraryEvent>()
    private val listed = LibraryUiState(
        tab = LibraryTab.Artists,
        viewMode = LibraryViewMode.List,
        artistCount = artists.size,
    )

    @Test
    fun `an artist row's more options opens that artist's menu`() {
        show(listed)

        compose.onAllNodesWithContentDescription("More options").assertCountEquals(artists.size)
        compose.onAllNodesWithContentDescription("More options")[1].performClick()
        compose.onNodeWithText("Shuffle albums").performClick()

        assertThat(events).containsExactly(LibraryEvent.ArtistMenu(artists[1], MediaAction.ShuffleAlbums))
    }

    @Test
    fun `artist rows have no more options while selecting`() {
        show(listed.copy(selection = LibrarySelection.Artists(persistentListOf(artists[0]))))

        compose.onAllNodesWithContentDescription("More options").assertCountEquals(0)
    }

    private fun show(state: LibraryUiState) {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.from(artists)).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = { events += it },
                )
            }
        }
    }
}
