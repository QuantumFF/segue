package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.Song
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryRestoreTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `no tab shows until the last one is restored`() {
        var state by mutableStateOf(LibraryUiState(isRestoring = true))
        compose.setContent {
            SegueTheme {
                LibraryContent(
                    state = state,
                    albums = flowOf(PagingData.empty<Album>()).collectAsLazyPagingItems(),
                    artists = flowOf(PagingData.empty<Artist>()).collectAsLazyPagingItems(),
                    songs = flowOf(PagingData.empty<Song>()).collectAsLazyPagingItems(),
                    onEvent = {},
                )
            }
        }

        compose.onNodeWithText("Albums").assertDoesNotExist()

        state = LibraryUiState(tab = LibraryTab.Songs)
        compose.onNodeWithText("Songs").assertIsSelected()
    }
}
