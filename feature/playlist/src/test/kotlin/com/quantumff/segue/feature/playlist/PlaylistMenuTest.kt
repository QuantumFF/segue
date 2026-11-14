package com.quantumff.segue.feature.playlist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The playlist page's ⋮ is there for everyone; the owner also gets Rename and Delete, and a
 * top-bar pencil into edit mode instead of an Edit item (DESIGN.md §4.5).
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val menuActions = mutableListOf<MediaAction>()
    private var editClicks = 0

    @Test
    fun `a shared playlist has a full menu but no pencil, Rename or Delete`() {
        show(readonly = true)

        compose.onNodeWithContentDescription("Edit").assertDoesNotExist()
        openMenu()
        compose.onNodeWithText("Play next").assertExists()
        compose.onNodeWithText("Add to queue").assertExists()
        compose.onNodeWithText("Download").assertExists()
        compose.onNodeWithText("Rename").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Add to playlist").performClick()

        assertThat(menuActions).containsExactly(MediaAction.AddToPlaylist)
    }

    @Test
    fun `an owned playlist adds Rename and Delete, and its pencil starts edit mode`() {
        show(readonly = false)

        openMenu()
        compose.onNodeWithText("Rename").assertExists()
        compose.onNodeWithText("Delete").assertExists()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Play next").performClick()
        compose.onNodeWithContentDescription("Edit").performClick()

        assertThat(menuActions).containsExactly(MediaAction.PlayNext)
        assertThat(editClicks).isEqualTo(1)
    }

    private fun openMenu() = compose.onAllNodesWithContentDescription("More options").onFirst().performClick()

    private fun show(readonly: Boolean) {
        val state = PlaylistUiState(
            playlist = TestData.playlist("p1", name = "Road trip", readonly = readonly),
            entries = persistentListOf(PlaylistEntry(TestData.song("a"))),
            loadState = LoadState.Loaded,
        )
        compose.setContent {
            SegueTheme {
                PlaylistScreenContent(
                    uiState = state,
                    onBackClick = {},
                    onPlayClick = {},
                    onShuffleClick = {},
                    onShuffleAlbumsClick = {},
                    onTrackClick = {},
                    onTrackAction = { _, _, _ -> },
                    onTrackSwipe = { _, _ -> },
                    onPlaylistAction = { menuActions += it },
                    onEditClick = { editClicks++ },
                    onCancelEditClick = {},
                    onSaveClick = {},
                    onEditBack = {},
                    onKeepEditingClick = {},
                    onMove = { _, _ -> },
                    onRemoveEntry = {},
                    onRename = {},
                    onDelete = {},
                    onRetryClick = {},
                    onMessageDismiss = {},
                )
            }
        }
    }
}
