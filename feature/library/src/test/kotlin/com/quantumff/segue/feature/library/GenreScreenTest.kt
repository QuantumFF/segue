package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The genre grid offline and empty (DESIGN.md §5.8), its badges and its album menu. */
// Tall enough for a whole album menu.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class GenreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = listOf(TestData.album("1", genre = "Jazz"), TestData.album("2", genre = "Jazz"))
    private var shown by mutableStateOf(albums)
    private var state by mutableStateOf(GenreUiState(genre = "Jazz", albumCount = albums.size))
    private val taps = mutableListOf<String>()
    private val actions = mutableListOf<Pair<Album, MediaAction>>()

    private fun show() {
        compose.setContent {
            SegueTheme {
                GenreContent(
                    state = state,
                    albums = flowOf(PagingData.from(shown)).collectAsLazyPagingItems(),
                    onBackClick = {},
                    onAlbumClick = { taps += "album $it" },
                    onAlbumAction = { album, action -> actions += album to action },
                    onPlayAlbums = { _, _ -> },
                    onRetry = { taps += "retry" },
                    onOfflineModeSettingClick = { taps += "settings" },
                    onMessageDismiss = {},
                )
            }
        }
    }

    @Test
    fun `online there is no chip`() {
        show()

        compose.onNodeWithText("Offline", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without a network the chip shows downloads and a tap retries`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingDownloads)
        show()

        compose.onNodeWithText("Offline · showing downloads").assertIsDisplayed().performClick()

        assertThat(taps).containsExactly("retry")
    }

    @Test
    fun `in offline mode the chip says so and a tap opens the setting`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingDownloadsInOfflineMode)
        show()

        compose.onNodeWithText("Offline mode · showing downloads").assertIsDisplayed().performClick()

        assertThat(taps).containsExactly("settings")
    }

    @Test
    fun `a genre with no albums shows an empty state`() {
        shown = emptyList()
        state = state.copy(albumCount = 0)
        show()

        compose.onNodeWithText("No albums in this genre").assertIsDisplayed()
    }

    @Test
    fun `offline a genre with nothing downloaded says so under the chip`() {
        shown = emptyList()
        state = state.copy(albumCount = 0, offlineStatus = OfflineStatus.ShowingDownloads)
        show()

        compose.onNodeWithText("Offline · showing downloads").assertIsDisplayed()
        compose.onNodeWithText("Nothing downloaded in this genre").assertIsDisplayed()
    }

    @Test
    fun `a pinned album's card carries its download badge`() {
        state = state.copy(albumDownloads = persistentMapOf("1" to DownloadStatus.Downloaded))
        show()

        compose.onNodeWithContentDescription("Downloaded", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a long-press opens the album menu, which goes to the artist and adds to a playlist`() {
        show()

        compose.onNodeWithText(albums[0].name).performTouchInput { longClick() }
        compose.onNodeWithText("Add to playlist").assertIsDisplayed()
        compose.onNodeWithText("Go to artist").performClick()

        assertThat(actions).containsExactly(albums[0] to MediaAction.GoToArtist)
    }
}
