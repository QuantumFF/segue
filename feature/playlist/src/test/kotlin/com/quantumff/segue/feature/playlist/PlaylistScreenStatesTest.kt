package com.quantumff.segue.feature.playlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.SongAvailability
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Playlist detail's loading, error, not-found and empty states. */
@RunWith(RobolectricTestRunner::class)
class PlaylistScreenStatesTest {
    @get:Rule
    val compose = createComposeRule()

    private var retries = 0
    private var backs = 0

    private val playlist = TestData.playlist("p1", name = "Road trip")
    private val entries = persistentListOf(PlaylistEntry(TestData.song("a")), PlaylistEntry(TestData.song("b")))

    @Test
    fun `while loading there is a back bar but no Play or Download`() {
        show(PlaylistUiState(loadState = LoadState.Loading, isRefreshing = true))
        compose.mainClock.advanceTimeBy(SKELETON_DELAY_MS)

        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
        compose.onNodeWithContentDescription("Download").assertDoesNotExist()
    }

    @Test
    fun `a failed load with nothing cached offers Retry under a back bar`() {
        show(PlaylistUiState(loadState = LoadState.Failed))

        compose.onNodeWithText("Couldn’t load this playlist").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()

        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun `a deleted playlist is not found`() {
        show(PlaylistUiState(playlist = playlist, entries = entries, loadState = LoadState.NotFound))

        compose.onNodeWithText("Playlist not found").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Road trip").assertDoesNotExist()
    }

    @Test
    fun `an empty playlist points to the track menus`() {
        show(PlaylistUiState(playlist = playlist, loadState = LoadState.Loaded))

        compose.onAllNodesWithText("Road trip").onFirst().assertIsDisplayed()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("empty")
        compose.onNodeWithText(EMPTY_TITLE).assertExists()
        compose.onNodeWithText(EMPTY_MESSAGE).assertExists()
    }

    @Test
    fun `a playlist cached without its songs shows no empty state while its refresh runs`() {
        show(PlaylistUiState(playlist = playlist, loadState = LoadState.Loaded, isRefreshing = true))

        compose.onAllNodesWithText("Road trip").onFirst().assertIsDisplayed()
        compose.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()
    }

    @Test
    fun `Play is disabled when nothing is Available`() {
        val offline = SongAvailability(isOffline = true)
        show(
            PlaylistUiState(
                playlist = playlist,
                entries = entries,
                loadState = LoadState.Loaded,
                availability = offline,
            ),
        )

        compose.onNodeWithContentDescription("Play").assertIsNotEnabled()
    }

    @Test
    fun `Play is enabled when a song is Available`() {
        show(PlaylistUiState(playlist = playlist, entries = entries, loadState = LoadState.Loaded))

        compose.onNodeWithContentDescription("Play").assertIsEnabled()
    }

    @Test
    fun `cached rows with a failed refresh show with the cached chip`() {
        show(PlaylistUiState(playlist = playlist, entries = entries, loadState = LoadState.Loaded, isOffline = true))

        compose.onNode(hasScrollToKeyAction()).performScrollToKey("offline")
        compose.onNodeWithText("Offline · showing cached playlist").assertExists()
        compose.onNodeWithText(entries.first().song.title).assertExists()
    }

    private fun show(state: PlaylistUiState) {
        compose.setContent {
            SegueTheme {
                PlaylistScreenContent(
                    uiState = state,
                    onBackClick = { backs++ },
                    onPlayClick = {},
                    onShuffleClick = {},
                    onShuffleAlbumsClick = {},
                    onTrackClick = {},
                    onTrackAction = { _, _, _ -> },
                    onTrackSwipe = { _, _ -> },
                    onEditClick = {},
                    onCancelEditClick = {},
                    onSaveClick = {},
                    onEditBack = {},
                    onKeepEditingClick = {},
                    onMove = { _, _ -> },
                    onRemoveEntry = {},
                    onRename = {},
                    onDelete = {},
                    onRetryClick = { retries++ },
                    onMessageDismiss = {},
                )
            }
        }
    }

    private companion object {
        const val SKELETON_DELAY_MS = 200L
        const val EMPTY_TITLE = "This playlist is empty"
        const val EMPTY_MESSAGE = "Add songs from any ⋮ menu"
    }
}
