package com.quantumff.segue.feature.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The playlist list's states, shared by the Playlists screen and the Library's Playlists tab. */
@RunWith(RobolectricTestRunner::class)
class PlaylistsBodyTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(PlaylistsUiState(isLoading = false))
    private var retries = 0

    @Test
    fun `while the first fetch runs there is no empty state`() {
        state = state.copy(isFetching = true)
        show()

        compose.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()

        state = state.copy(isFetching = false)
        compose.onNodeWithText(EMPTY_TITLE).assertIsDisplayed()
    }

    @Test
    fun `a failed fetch with no playlists offers Retry`() {
        state = state.copy(fetchFailed = true)
        show()

        compose.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()

        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun `offline with no pinned playlists says so, without Create or Retry`() {
        state = state.copy(isOffline = true, fetchFailed = true)
        show()

        compose.onNodeWithText("You're offline").assertIsDisplayed()
        compose.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                PlaylistsBody(
                    uiState = state,
                    creating = false,
                    onCreatingChange = {},
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
        const val EMPTY_TITLE = "No playlists yet"
    }
}
