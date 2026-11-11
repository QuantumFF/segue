package com.quantumff.segue.feature.artist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.PinAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The artist page offers Download from its ⋮ menu, not its action row (DESIGN.md §4.4), and the menu
 * item does what the row's button did: it pins the artist, or opens the download sheet once pinned.
 */
@RunWith(RobolectricTestRunner::class)
class ArtistDownloadMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private var downloads = 0
    private val pinActions = mutableListOf<PinAction>()
    private val artistActions = mutableListOf<MediaAction>()

    @Test
    fun `the action row has no Download button`() {
        show(download = null)

        compose.onNodeWithContentDescription("Download").assertDoesNotExist()
        compose.onNodeWithContentDescription("Instant mix").assertExists()
    }

    @Test
    fun `Download in the menu pins the artist`() {
        show(download = null)

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Download").performClick()

        assertThat(downloads).isEqualTo(1)
        assertThat(artistActions).isEmpty()
    }

    @Test
    fun `once pinned the menu opens the download sheet for the pin's status`() {
        show(download = DownloadStatus.Downloading(0.4f))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Remove download").performClick()
        compose.onNodeWithText("Pause download").performClick()

        assertThat(pinActions).containsExactly(PinAction.Pause)
        assertThat(artistActions).isEmpty()
    }

    private fun show(download: DownloadStatus?) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                ArtistScreen(
                    state = PreviewArtistState.copy(download = download),
                    onBackClick = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onPlay = {},
                    onHeroPlay = {},
                    onPlayTopSong = {},
                    onShuffleAlbums = {},
                    onInstantMix = {},
                    onToggleStar = {},
                    onRetry = {},
                    onMessageDismiss = {},
                    onDownload = { downloads++ },
                    onPinAction = { pinActions += it },
                    onArtistAction = { artistActions += it },
                )
            }
        }
    }
}
