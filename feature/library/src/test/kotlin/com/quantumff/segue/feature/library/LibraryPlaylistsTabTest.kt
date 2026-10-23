package com.quantumff.segue.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryPlaylistsTabTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(LibraryUiState(tab = LibraryTab.Playlists, playlists = persistentListOf()))

    @Test
    fun `the plus on the Playlists tab opens the tab's create dialog`() {
        show()

        compose.onNodeWithText("Create dialog").assertDoesNotExist()
        compose.onNodeWithContentDescription("New playlist").performClick()

        compose.onNodeWithText("Create dialog").assertIsDisplayed()
    }

    @Test
    fun `closing the create dialog lets the plus open it again`() {
        show()

        compose.onNodeWithContentDescription("New playlist").performClick()
        compose.onNodeWithText("Create dialog").performClick()
        compose.onNodeWithText("Create dialog").assertDoesNotExist()
        compose.onNodeWithContentDescription("New playlist").performClick()

        compose.onNodeWithText("Create dialog").assertIsDisplayed()
    }

    @Test
    fun `only the Playlists tab shows the plus`() {
        state = state.copy(tab = LibraryTab.Albums)
        show()

        compose.onNodeWithContentDescription("New playlist").assertDoesNotExist()
    }

    @Test
    fun `the Playlists tab shows the slot`() {
        show()

        compose.onNodeWithText("Shared playlists body").assertIsDisplayed()
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = {},
                    playlistsTab = { creating, onCreatingChange ->
                        // Stands in for the playlist feature's shared body, which owns the create dialog.
                        Column {
                            Text("Shared playlists body")
                            if (creating) {
                                Text("Create dialog", Modifier.clickable { onCreatingChange(false) })
                            }
                        }
                    },
                )
            }
        }
    }
}
