package com.quantumff.segue.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.UserPreferences
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

/** The Library opened on a chosen tab: Home's "See all" on Genres (DESIGN.md §4.1). */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(RobolectricTestRunner::class)
class LibraryTabRequestTest {
    @get:Rule
    val compose = createComposeRule()

    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val library = FakeLibraryRepository().apply {
        genresState.value = listOf(TestData.genre("Jazz"), TestData.genre("Rock"))
    }
    private val playlists = FakePlaylistRepository()
    private val stars = FakeStarRatingRepository()
    private val downloads = FakeDownloadRepository()
    private val play = RecordingPlayIntents()
    private val servers = FakeServerRepository().apply {
        serversState.value = listOf(TestData.server)
        activeId.value = TestData.server.id
    }

    /** The user left the Library on Songs. */
    private val preferences = FakeUserPreferencesRepository().apply {
        preferences.value = UserPreferences(libraryTab = LibraryTab.Songs)
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
        mediaActionHandler(play, downloads, stars, albums, playlists, artists),
        preferences,
        FakePlayerStateSource(),
    )

    private var requestedTab by mutableStateOf<LibraryTab?>(null)
    private var shown = 0

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
                    requestedTab = requestedTab,
                    onRequestedTabShow = {
                        shown++
                        requestedTab = null
                    },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun `a requested tab shows instead of the last one, once`() {
        requestedTab = LibraryTab.Genres
        show()

        tab("Genres").assertIsSelected()
        assertThat(shown).isEqualTo(1)

        tab("Albums").performScrollTo().performClick()
        tab("Albums").assertIsSelected()
        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun `a request reaching a Library already open switches its tab`() {
        show()
        tab("Songs").assertIsSelected()

        requestedTab = LibraryTab.Genres

        tab("Genres").assertIsSelected()
        assertThat(shown).isEqualTo(1)
    }
}
