package com.quantumff.segue.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.OfflineStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's offline chip: its copy for each case, and what a tap does (DESIGN.md §5.8). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class HomeOfflineTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(PreviewHomeState)
    private val taps = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            SegueTheme {
                HomeScreen(
                    uiState = state,
                    onAccountClick = {},
                    onAlbumClick = {},
                    onPlaylistClick = {},
                    onGenreClick = {},
                    onSeeAllClick = {},
                    onPlayAlbum = {},
                    onAlbumAction = { _, _ -> },
                    onRefresh = { taps += "refresh" },
                    onRefreshExplore = {},
                    onRetrySync = {},
                    onMessageDismiss = {},
                    onOfflineModeSettingClick = { taps += "settings" },
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
    fun `without a network the chip shows downloads and a tap retries the refresh`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingDownloads)
        show()

        compose.onNodeWithText("Offline · showing downloads").assertIsDisplayed().performClick()

        assertThat(taps).containsExactly("refresh")
    }

    @Test
    fun `with nothing downloaded the chip still shows`() {
        state = HomeUiState(isLoading = false, offlineStatus = OfflineStatus.ShowingDownloads)
        show()

        compose.onNodeWithText("Offline · showing downloads").assertIsDisplayed()
    }

    @Test
    fun `in offline mode the chip says so and a tap opens the setting`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingDownloadsInOfflineMode)
        show()

        compose.onNodeWithText("Offline mode · showing downloads").assertIsDisplayed().performClick()

        assertThat(taps).containsExactly("settings")
    }

    @Test
    fun `an unreachable server still shows the cached library chip, which retries`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingCachedLibrary)
        show()

        compose.onNodeWithText("Offline · showing cached library").assertIsDisplayed().performClick()

        assertThat(taps).containsExactly("refresh")
    }
}
