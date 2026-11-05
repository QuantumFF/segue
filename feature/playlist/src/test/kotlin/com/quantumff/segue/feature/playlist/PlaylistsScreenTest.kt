package com.quantumff.segue.feature.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Playlists screen (bottom-nav destination): its top bar, offline chip and states. */
@RunWith(RobolectricTestRunner::class)
class PlaylistsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        PlaylistsUiState(playlists = persistentListOf(TestData.playlist("p1")), isLoading = false),
    )
    private var retries = 0
    private var offlineModeSettingOpened = 0

    @Test
    fun `online there is no chip and New playlist is offered`() {
        show()

        compose.onNodeWithText(OFFLINE_DOWNLOADS).assertDoesNotExist()
        compose.onNodeWithText(OFFLINE_CACHED).assertDoesNotExist()
        compose.onNodeWithContentDescription(CREATE).assertIsDisplayed()
    }

    @Test
    fun `offline without a network the chip retries and New playlist is hidden`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingDownloads)
        show()

        compose.onNodeWithText(OFFLINE_DOWNLOADS).performClick()

        assertThat(retries).isEqualTo(1)
        assertThat(offlineModeSettingOpened).isEqualTo(0)
        compose.onNodeWithContentDescription(CREATE).assertDoesNotExist()
    }

    @Test
    fun `in Offline mode the chip opens the Offline mode setting`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingDownloadsInOfflineMode)
        show()

        compose.onNodeWithText("Offline mode · showing downloads").performClick()

        assertThat(offlineModeSettingOpened).isEqualTo(1)
        assertThat(retries).isEqualTo(0)
    }

    @Test
    fun `an unreachable server with cached playlists shows the cached chip, which retries`() {
        state = state.copy(offlineStatus = OfflineStatus.ShowingCachedLibrary)
        show()

        compose.onNodeWithText(OFFLINE_CACHED).performClick()

        assertThat(retries).isEqualTo(1)
        compose.onNodeWithContentDescription(CREATE).assertIsDisplayed()
    }

    @Test
    fun `an unreachable server with nothing cached shows the error, not the chip`() {
        state =
            PlaylistsUiState(isLoading = false, fetchFailed = true, offlineStatus = OfflineStatus.ShowingCachedLibrary)
        show()

        compose.onNodeWithText(OFFLINE_CACHED).assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun `offline with no pinned playlists says only downloaded ones show, without Create`() {
        state = PlaylistsUiState(isLoading = false, offlineStatus = OfflineStatus.ShowingDownloads)
        show()

        compose.onNodeWithText(OFFLINE_DOWNLOADS).assertIsDisplayed()
        compose.onNodeWithText("Only downloaded playlists show offline", substring = true).assertIsDisplayed()
        compose.onNodeWithText(CREATE).assertDoesNotExist()
        compose.onNodeWithContentDescription(CREATE).assertDoesNotExist()
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                PlaylistsScreenContent(
                    uiState = state,
                    onAccountClick = {},
                    onOfflineModeSettingClick = { offlineModeSettingOpened++ },
                    onPlaylistClick = {},
                    onRefresh = {},
                    onRetry = { retries++ },
                    onCreate = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onMenuAction = { _, _ -> },
                    onMessageDismiss = {},
                )
            }
        }
    }

    private companion object {
        const val CREATE = "New playlist"
        const val OFFLINE_DOWNLOADS = "Offline · showing downloads"
        const val OFFLINE_CACHED = "Offline · showing cached library"
    }
}
