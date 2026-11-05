package com.quantumff.segue.feature.album

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The album page's download sheet offers what fits the pin's download status. */
@RunWith(RobolectricTestRunner::class)
class AlbumPinSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<PinAction>()

    @Test
    fun `a failed download offers Retry and Remove`() {
        open(DownloadStatus.Failed, button = "Download failed")

        compose.onNodeWithText("Remove download").assertExists()
        compose.onNodeWithText("Pause download").assertDoesNotExist()
        compose.onNodeWithText("Retry download").performClick()

        assertThat(actions).containsExactly(PinAction.Retry)
        compose.onNodeWithText("Remove download").assertDoesNotExist()
    }

    @Test
    fun `a paused download offers Resume and Remove`() {
        open(DownloadStatus.Paused, button = "Download paused")

        compose.onNodeWithText("Remove download").assertExists()
        compose.onNodeWithText("Resume download").performClick()

        assertThat(actions).containsExactly(PinAction.Resume)
    }

    @Test
    fun `a download in progress offers Pause and Cancel`() {
        open(DownloadStatus.Downloading(0.4f), button = "Downloading")

        compose.onNodeWithText("Remove download").assertDoesNotExist()
        compose.onNodeWithText("Pause download").performClick()
        compose.onNodeWithContentDescription("Downloading").performClick()
        compose.onNodeWithText("Cancel download").performClick()

        assertThat(actions).containsExactly(PinAction.Pause, PinAction.Cancel).inOrder()
    }

    @Test
    fun `a finished download offers only Remove`() {
        open(DownloadStatus.Downloaded, button = "Downloaded")

        compose.onNodeWithText("Retry download").assertDoesNotExist()
        compose.onNodeWithText("Cancel download").assertDoesNotExist()
        compose.onNodeWithText("Remove download").performClick()

        assertThat(actions).containsExactly(PinAction.Remove)
    }

    /** Shows the album pinned with [status] and taps its download button, labelled [button]. */
    private fun open(status: DownloadStatus, button: String) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                AlbumScreen(
                    state = PreviewAlbumState.copy(download = status),
                    onBackClick = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onGenreClick = {},
                    onPlay = { _, _ -> },
                    onHeroPlay = {},
                    onShuffleAlbums = {},
                    onToggleStar = {},
                    onRatingChange = {},
                    onRetry = {},
                    onMessageDismiss = {},
                    onPinAction = { actions += it },
                )
            }
        }
        compose.onNodeWithContentDescription(button).performClick()
    }
}
