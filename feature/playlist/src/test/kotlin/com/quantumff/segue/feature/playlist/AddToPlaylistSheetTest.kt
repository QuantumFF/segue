package com.quantumff.segue.feature.playlist

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddToPlaylistSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val roadTrip = TestData.playlist("mine", name = "Road trip")

    private fun show(uiState: AddToPlaylistUiState, onPlaylistClick: (Playlist) -> Unit = {}) {
        compose.setContent {
            SegueTheme {
                AddToPlaylistSheetContent(
                    uiState = uiState,
                    onPlaylistClick = onPlaylistClick,
                    onNewPlaylistClick = {},
                )
            }
        }
    }

    @Test
    fun `the header counts the songs being added`() {
        show(AddToPlaylistUiState(subject = AddToPlaylistSubject(3, TestData.song("s1", title = "Nude"))))

        compose.onNodeWithText("3 songs").assert(isHeading())
        compose.onNodeWithText("Add to playlist").assertExists()
    }

    @Test
    fun `the header names a single song`() {
        show(AddToPlaylistUiState(subject = AddToPlaylistSubject(1, TestData.song("s1", title = "Nude"))))

        compose.onNodeWithText("Nude").assert(isHeading())
    }

    @Test
    fun `a single song the library doesn't know is counted`() {
        show(AddToPlaylistUiState(subject = AddToPlaylistSubject(1, firstSong = null)))

        compose.onNodeWithText("1 song").assert(isHeading())
    }

    @Test
    fun `playlist rows are disabled while saving`() {
        val clicked = mutableListOf<Playlist>()
        show(AddToPlaylistUiState(playlists = persistentListOf(roadTrip), isSaving = true), clicked::add)

        compose.onNodeWithText("Road trip").assertIsNotEnabled().performClick()
        compose.onNodeWithText("New playlist").assertIsNotEnabled()
        assertThat(clicked).isEmpty()
    }

    @Test
    fun `playlist rows are enabled otherwise`() {
        show(AddToPlaylistUiState(playlists = persistentListOf(roadTrip)))

        compose.onNodeWithText("Road trip").assertIsEnabled()
    }
}
