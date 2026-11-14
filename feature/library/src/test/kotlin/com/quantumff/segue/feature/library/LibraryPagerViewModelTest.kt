package com.quantumff.segue.feature.library

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.FakePlayerStateSource
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeLibrarySyncRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Library's pager and the ViewModel's tab: selecting and the remembered tab follow the settled page. */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryPagerViewModelTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()
    private val downloads = FakeDownloadRepository()
    private val play = RecordingPlayIntents()
    private val preferences = FakeUserPreferencesRepository()
    private val servers = FakeServerRepository().apply {
        serversState.value = listOf(TestData.server)
        activeId.value = TestData.server.id
    }

    private val viewModel = LibraryViewModel(
        SavedStateHandle(),
        albums,
        library,
        FakeLibrarySyncRepository(),
        servers,
        downloads,
        offlineLibraryView(FakeOfflineModeRepository(), downloads, artists, library, playlists),
        play,
        mediaActionHandler(play, downloads, FakeStarRatingRepository(), albums, playlists, artists),
        preferences,
        FakePlayerStateSource(),
    )

    @Test
    fun `a swipe that settles on the next tab ends selecting and is the remembered tab`() {
        show()
        viewModel.toggleSelected(LibraryItem.OfAlbum(TestData.album(id = "1")))
        compose.waitForIdle()

        compose.onNodeWithTag(LIBRARY_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertThat(viewModel.uiState.value.tab).isEqualTo(LibraryTab.Artists)
        assertThat(viewModel.uiState.value.selection).isNull()
        assertThat(preferences.preferences.value.libraryTab).isEqualTo(LibraryTab.Artists)
    }

    @Test
    fun `a swipe that snaps back keeps selecting and the remembered tab`() {
        show()
        viewModel.toggleSelected(LibraryItem.OfAlbum(TestData.album(id = "1")))
        compose.waitForIdle()

        compose.onNodeWithTag(LIBRARY_PAGER_TAG).performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * SHORT_SWIPE, durationMillis = SLOW_SWIPE_MILLIS)
        }
        compose.waitForIdle()

        assertThat(viewModel.uiState.value.tab).isEqualTo(LibraryTab.Albums)
        assertThat(viewModel.uiState.value.selection).isNotNull()
        assertThat(preferences.preferences.value.libraryTab).isEqualTo(LibraryTab.Albums)
    }

    @Test
    fun `a tapped tab is remembered once the pager settles on it`() {
        show()

        compose.onNode(hasText("Downloads") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(viewModel.uiState.value.tab).isEqualTo(LibraryTab.Downloads)
        assertThat(preferences.preferences.value.libraryTab).isEqualTo(LibraryTab.Downloads)
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                LibraryScreen(
                    onAccountClick = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onGenreClick = {},
                    onDownloadsClick = {},
                    onAddToPlaylist = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    private companion object {
        const val SHORT_SWIPE = 0.2f
        const val SLOW_SWIPE_MILLIS = 1_000L
    }
}
