package com.quantumff.segue.feature.album

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.OfflineStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The album page's offline chip, which behaves as every other screen's (DESIGN.md §5.8). */
@RunWith(RobolectricTestRunner::class)
class AlbumOfflineChipTest {
    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()

    @Test
    fun `online there is no chip`() {
        show(OfflineStatus.Online)

        compose.onNodeWithText("Offline", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an unreachable server shows the cached library chip, which retries`() {
        tapChip(OfflineStatus.ShowingCachedLibrary, "Offline · showing cached library")

        assertThat(taps).containsExactly("retry")
    }

    @Test
    fun `without a network the chip shows downloads and retries`() {
        tapChip(OfflineStatus.ShowingDownloads, "Offline · showing downloads")

        assertThat(taps).containsExactly("retry")
    }

    @Test
    fun `in Offline mode the chip opens the setting`() {
        tapChip(OfflineStatus.ShowingDownloadsInOfflineMode, "Offline mode · showing downloads")

        assertThat(taps).containsExactly("settings")
    }

    private fun tapChip(status: OfflineStatus, label: String) {
        show(status)
        compose.onNode(hasScrollToKeyAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
    }

    private fun show(status: OfflineStatus) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                AlbumScreen(
                    state = PreviewAlbumState.copy(offlineStatus = status),
                    onBackClick = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onGenreClick = {},
                    onPlay = { _, _ -> },
                    onHeroPlay = {},
                    onShuffleAlbums = {},
                    onToggleStar = {},
                    onRatingChange = {},
                    onRetry = { taps += "retry" },
                    onMessageDismiss = {},
                    onOfflineModeSettingClick = { taps += "settings" },
                )
            }
        }
    }
}
