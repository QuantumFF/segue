package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Artist
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

// Wide and tall enough for two grid columns and a whole album menu.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibrarySelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = List(3) { TestData.album(id = "$it") }
    private val pages = flowOf(PagingData.from(albums))
    private var state by mutableStateOf(LibraryUiState(albumCount = albums.size))
    private val events = mutableListOf<LibraryEvent>()

    @Test
    fun `a long-press starts selecting and a tap then picks instead of opening`() {
        show()

        compose.onNodeWithText(albums[0].name).performTouchInput { longClick() }
        state = state.copy(selection = LibrarySelection.Albums(persistentListOf(albums[0])))
        compose.onNodeWithText(albums[1].name).performClick()

        assertThat(events).containsExactly(
            LibraryEvent.ToggleSelected(MenuTarget.ForAlbum(albums[0])),
            LibraryEvent.ToggleSelected(MenuTarget.ForAlbum(albums[1])),
        ).inOrder()
    }

    @Test
    fun `the selection bar counts the picks and acts on them`() {
        state = state.copy(selection = LibrarySelection.Albums(persistentListOf(albums[2], albums[0])))
        show()

        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play selected").performClick()
        compose.onNodeWithContentDescription("Clear selection").performClick()

        assertThat(events).containsExactly(
            LibraryEvent.SelectionAction(MediaAction.Play),
            LibraryEvent.ClearSelection,
        ).inOrder()
    }

    @Test
    fun `picked albums are marked selected`() {
        state = state.copy(selection = LibrarySelection.Albums(persistentListOf(albums[1])))
        show()

        compose.onNode(hasText(albums[1].name) and isSelected()).assertExists()
        compose.onAllNodes(hasText(albums[0].name) and isSelected()).assertCountEquals(0)
    }

    @Test
    fun `the selection menu offers what applies to every pick`() {
        state = state.copy(selection = LibrarySelection.Albums(persistentListOf(albums[0], albums[1])))
        show()

        compose.onNodeWithContentDescription("Selection options").performClick()
        compose.onNodeWithText("2 albums").assertIsDisplayed()
        compose.onNodeWithText("Shuffle albums").performClick()

        assertThat(events).containsExactly(LibraryEvent.SelectionAction(MediaAction.ShuffleAlbums))
    }

    @Test
    fun `one pick's menu is its own context menu, and acting on it ends selecting`() {
        state = state.copy(selection = LibrarySelection.Albums(persistentListOf(albums[0])))
        show()

        compose.onNodeWithContentDescription("Selection options").performClick()
        // Shuffle is the album menu's own, not the selection's (Shuffle albums).
        compose.onNodeWithText("Shuffle").performClick()

        assertThat(events).containsExactly(
            LibraryEvent.AlbumMenu(albums[0], MediaAction.Shuffle),
            LibraryEvent.ClearSelection,
        ).inOrder()
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = pages.collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = { events += it },
                )
            }
        }
    }
}
