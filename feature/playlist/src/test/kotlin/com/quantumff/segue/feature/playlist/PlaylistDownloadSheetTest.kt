package com.quantumff.segue.feature.playlist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinAction
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The playlist page's download sheet offers what fits the pin's download status. */
@RunWith(RobolectricTestRunner::class)
class PlaylistDownloadSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<PinAction>()
    private var downloads = 0

    @Test
    fun `a playlist that is not pinned downloads without a sheet`() {
        show(download = null)

        compose.onNodeWithContentDescription("Download").performClick()

        assertThat(downloads).isEqualTo(1)
        compose.onNodeWithText("Remove download").assertDoesNotExist()
    }

    @Test
    fun `a failed download offers Retry and Remove`() {
        show(DownloadStatus.Failed)
        compose.onNodeWithContentDescription("Download failed").performClick()

        compose.onNodeWithText("Remove download").assertExists()
        compose.onNodeWithText("Retry download").performClick()

        assertThat(actions).containsExactly(PinAction.Retry)
    }

    @Test
    fun `a paused download offers Resume and Remove`() {
        show(DownloadStatus.Paused)
        compose.onNodeWithContentDescription("Download paused").performClick()

        compose.onNodeWithText("Remove download").assertExists()
        compose.onNodeWithText("Resume download").performClick()

        assertThat(actions).containsExactly(PinAction.Resume)
    }

    @Test
    fun `a queued download offers Pause and Cancel`() {
        show(DownloadStatus.Queued)
        compose.onNodeWithContentDescription("Download queued").performClick()

        compose.onNodeWithText("Pause download").assertExists()
        compose.onNodeWithText("Remove download").assertDoesNotExist()
        compose.onNodeWithText("Cancel download").performClick()

        assertThat(actions).containsExactly(PinAction.Cancel)
    }

    private fun show(download: DownloadStatus?) {
        val state = PlaylistUiState(
            playlist = TestData.playlist("p1", name = "Road trip"),
            entries = persistentListOf(PlaylistEntry(TestData.song("a"))),
            loadState = LoadState.Loaded,
            download = download,
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
                    onEditClick = {},
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
                    onDownloadClick = { downloads++ },
                    onPinAction = { actions += it },
                )
            }
        }
    }
}
