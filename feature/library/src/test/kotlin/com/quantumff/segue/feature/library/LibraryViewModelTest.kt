package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.Event
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.ArtistFilter
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.LibraryViewMode
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.ScreenState
import com.quantumff.segue.core.model.SongFilter
import com.quantumff.segue.core.model.SongSortOrder
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.model.UserPreferences
import com.quantumff.segue.core.testing.MainDispatcherRule
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
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()
    private val sync = FakeLibrarySyncRepository()
    private val stars = FakeStarRatingRepository()
    private val servers = FakeServerRepository()
    private val downloads = FakeDownloadRepository()
    private val offlineMode = FakeOfflineModeRepository()
    private val savedState = SavedStateHandle()
    private val play = RecordingPlayIntents()
    private val preferences = FakeUserPreferencesRepository()
    private val player = FakePlayerStateSource()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        albums.albumsState.value = listOf(
            TestData.album("1", genre = "Rock", year = 2007, starredAt = 1L),
            TestData.album("2", genre = "Jazz", year = 2001),
            TestData.album("3", genre = "Rock", year = 2001),
        )
        artists.artistsState.value = listOf(TestData.artist("artist-1"))
        library.songsState.value = listOf(TestData.song("s1"), TestData.song("s2"))
        library.genresState.value = listOf(TestData.genre("Jazz"), TestData.genre("Rock"))
        servers.serversState.value = listOf(TestData.server)
        servers.activeId.value = TestData.server.id
        viewModel = newViewModel()
    }

    /** A ViewModel as after a restart ([savedState] empty) or a process death ([savedState] kept). */
    private fun newViewModel(savedState: SavedStateHandle = this.savedState) = LibraryViewModel(
        savedState,
        albums,
        library,
        sync,
        servers,
        downloads,
        offlineLibraryView(offlineMode, downloads, artists, library, playlists),
        play,
        mediaActionHandler(play, downloads, stars, albums, playlists, artists),
        preferences,
        player,
    )

    @Test
    fun thePlayingSongsRowsAreMarkedAndFollowThePlayer() = runTest {
        val s1 = TestData.song("s1")
        val s2 = TestData.song("s2")
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().currentSong).isNull()

            player.state.value = TestData.playerOn("s2", isPlaying = true)
            var state = expectMostRecentItem()
            assertThat(state.currentSong).isEqualTo(CurrentSong("s2", isPlaying = true))
            assertThat(listOf(s1, s2).map(state::isCurrent)).containsExactly(false, true).inOrder()

            player.state.value = TestData.playerOn("s2", isPlaying = false)
            assertThat(expectMostRecentItem().currentSong).isEqualTo(CurrentSong("s2", isPlaying = false))

            player.state.value = TestData.playerOn("s1", isPlaying = true)
            state = expectMostRecentItem()
            assertThat(listOf(s1, s2).map(state::isCurrent)).containsExactly(true, false).inOrder()

            player.state.value = PlayerUiState()
            assertThat(expectMostRecentItem().currentSong).isNull()
        }
    }

    @Test
    fun downloadedChipFiltersAlbumsToDownloadedOnes() = runTest {
        albums.downloadedAlbumIds = setOf("2")
        viewModel.uiState.test {
            viewModel.cycleDownloadedFilter(LibraryTab.Albums)
            val state = expectMostRecentItem()
            assertThat(state.albumFilter.downloaded).isTrue()
            assertThat(state.albumCount).isEqualTo(1)

            viewModel.clearFilters(LibraryTab.Albums)
            assertThat(expectMostRecentItem().albumFilter.downloaded).isNull()
        }
    }

    @Test
    fun downloadedChipCyclesThroughOnlyExcludedAndOff() = runTest {
        albums.downloadedAlbumIds = setOf("2")
        viewModel.uiState.test {
            viewModel.cycleDownloadedFilter(LibraryTab.Albums)
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)

            viewModel.cycleDownloadedFilter(LibraryTab.Albums)
            val excluded = expectMostRecentItem()
            assertThat(excluded.albumFilter.downloaded).isFalse()
            assertThat(excluded.chipFilters(LibraryTab.Albums)?.shownDownloaded).isFalse()
            assertThat(excluded.albumCount).isEqualTo(2)

            viewModel.cycleDownloadedFilter(LibraryTab.Albums)
            val off = expectMostRecentItem()
            assertThat(off.albumFilter.downloaded).isNull()
            assertThat(off.albumCount).isEqualTo(3)
        }
    }

    @Test
    fun offlineForcesTheDownloadedChipOnEvenWhenExcluded() = runTest {
        albums.downloadedAlbumIds = setOf("1")
        viewModel.cycleDownloadedFilter(LibraryTab.Albums)
        viewModel.cycleDownloadedFilter(LibraryTab.Albums)
        offlineMode.isOffline.value = true
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.chipFilters(LibraryTab.Albums)?.shownDownloaded).isTrue()
            assertThat(state.albumCount).isEqualTo(1)
        }
    }

    @Test
    fun offlineAlbumsTabShowsDownloadedAlbumsWithTheChipOn() = runTest {
        albums.downloadedAlbumIds = setOf("1")
        offlineMode.isOffline.value = true
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isOffline).isTrue()
            assertThat(state.chipFilters(LibraryTab.Albums)?.shownDownloaded).isTrue()
            assertThat(state.albumCount).isEqualTo(1)
        }
    }

    @Test
    fun offlineStatusShowsDownloadsOnlyWhileOffline() = runTest {
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.Online)

            offlineMode.isOffline.value = true
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingDownloads)

            offlineMode.isOffline.value = false
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.Online)
        }
    }

    @Test
    fun stateCarriesCountsServerNameAndBrowseLists() = runTest {
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.albumCount).isEqualTo(3)
            assertThat(state.artistCount).isEqualTo(1)
            assertThat(state.songCount).isEqualTo(2)
            assertThat(state.serverName).isEqualTo(TestData.server.name)
            assertThat(state.years).containsExactly(2007, 2001).inOrder()
            assertThat(state.genres.map { it.name }).containsExactly("Jazz", "Rock")
            assertThat(state.playlists).isEmpty()
        }
    }

    @Test
    fun favoritesFilterNarrowsTheCount() = runTest {
        viewModel.uiState.test {
            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            val state = expectMostRecentItem()
            assertThat(state.albumFilter).isEqualTo(AlbumFilter(starred = true))
            assertThat(state.albumCount).isEqualTo(1)

            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            val excluded = expectMostRecentItem()
            assertThat(excluded.albumFilter).isEqualTo(AlbumFilter(starred = false))
            assertThat(excluded.albumCount).isEqualTo(2)

            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            assertThat(expectMostRecentItem().albumFilter).isEqualTo(AlbumFilter())
        }
    }

    @Test
    fun genreAndYearFiltersCombineAndClear() = runTest {
        viewModel.uiState.test {
            viewModel.setGenreFilter("Rock")
            viewModel.setYearFilter(2001)
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)

            viewModel.clearFilters(LibraryTab.Albums)
            val cleared = expectMostRecentItem()
            assertThat(cleared.albumFilter.isActive).isFalse()
            assertThat(cleared.albumCount).isEqualTo(3)
        }
    }

    @Test
    fun sortTabAndViewModeSurviveInSavedState() = runTest {
        viewModel.uiState.test {
            viewModel.setAlbumSort(AlbumSortOrder.Year)
            viewModel.selectTab(LibraryTab.Genres)
            viewModel.toggleViewMode(LibraryTab.Albums)
            val state = expectMostRecentItem()
            assertThat(state.albumSort).isEqualTo(AlbumSortOrder.Year)
            assertThat(state.tab).isEqualTo(LibraryTab.Genres)
            assertThat(state.albumViewMode).isEqualTo(LibraryViewMode.List)
        }
        assertThat(savedState.get<LibraryTab>(LibraryViewModel.KEY_TAB)).isEqualTo(LibraryTab.Genres)
    }

    @Test
    fun albumsAndArtistsEachKeepTheirOwnView() = runTest {
        viewModel.uiState.test {
            viewModel.toggleViewMode(LibraryTab.Artists)
            var state = expectMostRecentItem()
            assertThat(state.artistViewMode).isEqualTo(LibraryViewMode.List)
            assertThat(state.albumViewMode).isEqualTo(LibraryViewMode.Grid)

            viewModel.toggleViewMode(LibraryTab.Albums)
            viewModel.toggleViewMode(LibraryTab.Artists)
            state = expectMostRecentItem()
            assertThat(state.albumViewMode).isEqualTo(LibraryViewMode.List)
            assertThat(state.artistViewMode).isEqualTo(LibraryViewMode.Grid)
        }
    }

    @Test
    fun aFreshViewModelReopensOnTheStoredTabWithEachTabsSortAndView() = runTest {
        preferences.preferences.value = UserPreferences(
            libraryTab = LibraryTab.Songs,
            albumSort = AlbumSortOrder.Year,
            artistSort = ArtistSortOrder.AlbumCount,
            songSort = SongSortOrder.PlayCount,
            albumViewMode = LibraryViewMode.List,
            artistViewMode = LibraryViewMode.Grid,
        )

        newViewModel(SavedStateHandle()).uiState.test {
            val shown = cancelAndConsumeRemainingEvents()
                .filterIsInstance<Event.Item<LibraryUiState>>()
                .map { it.value }
                .filterNot { it.isRestoring }
            // The default tab never shows first.
            assertThat(shown.map { it.tab }.distinct()).containsExactly(LibraryTab.Songs)
            val state = shown.last()
            assertThat(state.albumSort).isEqualTo(AlbumSortOrder.Year)
            assertThat(state.artistSort).isEqualTo(ArtistSortOrder.AlbumCount)
            assertThat(state.songSort).isEqualTo(SongSortOrder.PlayCount)
            assertThat(state.albumViewMode).isEqualTo(LibraryViewMode.List)
            assertThat(state.artistViewMode).isEqualTo(LibraryViewMode.Grid)
        }
    }

    @Test
    fun savedStateWinsOverPreferences() = runTest {
        preferences.preferences.value = UserPreferences(
            libraryTab = LibraryTab.Songs,
            albumSort = AlbumSortOrder.Year,
            artistSort = ArtistSortOrder.AlbumCount,
            albumViewMode = LibraryViewMode.List,
        )
        val saved = SavedStateHandle(
            mapOf(
                LibraryViewModel.KEY_TAB to LibraryTab.Genres,
                LibraryViewModel.KEY_ALBUM_SORT to AlbumSortOrder.Name,
                LibraryViewModel.KEY_ALBUM_VIEW_MODE to LibraryViewMode.Grid,
            ),
        )

        newViewModel(saved).uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.tab).isEqualTo(LibraryTab.Genres)
            assertThat(state.albumSort).isEqualTo(AlbumSortOrder.Name)
            assertThat(state.albumViewMode).isEqualTo(LibraryViewMode.Grid)
            // Nothing saved for these: the preferences fill them in.
            assertThat(state.artistSort).isEqualTo(ArtistSortOrder.AlbumCount)
            assertThat(state.songSort).isEqualTo(SongSortOrder.Title)
        }
    }

    @Test
    fun theTabSortsAndViewsAreKeptInPreferences() = runTest {
        viewModel.uiState.test {
            viewModel.selectTab(LibraryTab.Artists)
            viewModel.setAlbumSort(AlbumSortOrder.Artist)
            viewModel.setArtistSort(ArtistSortOrder.RecentlyAdded)
            viewModel.setSongSort(SongSortOrder.Album)
            viewModel.toggleViewMode(LibraryTab.Artists)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(preferences.preferences.value).isEqualTo(
            UserPreferences(
                libraryTab = LibraryTab.Artists,
                albumSort = AlbumSortOrder.Artist,
                artistSort = ArtistSortOrder.RecentlyAdded,
                songSort = SongSortOrder.Album,
                artistViewMode = LibraryViewMode.List,
            ),
        )
        newViewModel(SavedStateHandle()).uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.tab).isEqualTo(LibraryTab.Artists)
            assertThat(state.artistViewMode).isEqualTo(LibraryViewMode.List)
            assertThat(state.albumViewMode).isEqualTo(LibraryViewMode.Grid)
        }
    }

    @Test
    fun theArtistsTabsSortAndFiltersReachTheRepository() = runTest {
        artists.artistsState.value = listOf(
            TestData.artist("a", starredAt = 1L),
            TestData.artist("b", starredAt = 1L),
            TestData.artist("c"),
        )
        artists.downloadedArtistIds = setOf("a")
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.artists.collect {} }
        viewModel.uiState.test {
            viewModel.setArtistSort(ArtistSortOrder.AlbumCount)
            viewModel.cycleFavoritesFilter(LibraryTab.Artists)
            viewModel.cycleDownloadedFilter(LibraryTab.Artists)
            viewModel.cycleDownloadedFilter(LibraryTab.Artists)
            val state = expectMostRecentItem()
            assertThat(state.artistSort).isEqualTo(ArtistSortOrder.AlbumCount)
            assertThat(state.artistFilter).isEqualTo(ArtistFilter(starred = true, downloaded = false))
            assertThat(state.chipFilters(LibraryTab.Artists))
                .isEqualTo(ChipFilters(favorites = true, downloaded = false, isOffline = false))
            assertThat(state.artistCount).isEqualTo(1)
        }
        assertThat(artists.lastSort).isEqualTo(ArtistSortOrder.AlbumCount)
        assertThat(artists.lastFilter).isEqualTo(ArtistFilter(starred = true, downloaded = false))
    }

    @Test
    fun theSongsTabsSortAndFiltersReachTheRepository() = runTest {
        library.songsState.value = listOf(
            TestData.song("a", starredAt = 1L),
            TestData.song("b", starredAt = 1L),
            TestData.song("c"),
        )
        library.downloadedSongsState.value = listOf(TestData.song("a", starredAt = 1L))
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.songs.collect {} }
        viewModel.uiState.test {
            viewModel.setSongSort(SongSortOrder.PlayCount)
            viewModel.cycleFavoritesFilter(LibraryTab.Songs)
            viewModel.cycleDownloadedFilter(LibraryTab.Songs)
            viewModel.cycleDownloadedFilter(LibraryTab.Songs)
            val state = expectMostRecentItem()
            assertThat(state.songSort).isEqualTo(SongSortOrder.PlayCount)
            assertThat(state.songFilter).isEqualTo(SongFilter(starred = true, downloaded = false))
            assertThat(state.chipFilters(LibraryTab.Songs))
                .isEqualTo(ChipFilters(favorites = true, downloaded = false, isOffline = false))
            assertThat(state.songCount).isEqualTo(1)
        }
        assertThat(library.lastSort).isEqualTo(SongSortOrder.PlayCount)
        assertThat(library.lastFilter).isEqualTo(SongFilter(starred = true, downloaded = false))
    }

    @Test
    fun otherTabsSortAndFiltersDoNotLeakIntoSongs() = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.songs.collect {} }
        viewModel.uiState.test {
            viewModel.setAlbumSort(AlbumSortOrder.Year)
            viewModel.setArtistSort(ArtistSortOrder.AlbumCount)
            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            viewModel.cycleDownloadedFilter(LibraryTab.Artists)
            val state = expectMostRecentItem()
            assertThat(state.songSort).isEqualTo(SongSortOrder.Title)
            assertThat(state.songFilter).isEqualTo(SongFilter())
            assertThat(state.songCount).isEqualTo(2)
        }
        assertThat(library.lastSort).isEqualTo(SongSortOrder.Title)
        assertThat(library.lastFilter).isEqualTo(SongFilter())
    }

    @Test
    fun songsSortAndFiltersDoNotLeakIntoOtherTabs() = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.albums.collect {} }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.artists.collect {} }
        viewModel.uiState.test {
            viewModel.setSongSort(SongSortOrder.Year)
            viewModel.cycleFavoritesFilter(LibraryTab.Songs)
            viewModel.cycleDownloadedFilter(LibraryTab.Songs)
            val state = expectMostRecentItem()
            assertThat(state.albumFilter).isEqualTo(AlbumFilter())
            assertThat(state.artistFilter).isEqualTo(ArtistFilter())
        }
        assertThat(albums.lastFilter).isEqualTo(AlbumFilter())
        assertThat(artists.lastFilter).isEqualTo(ArtistFilter())
    }

    @Test
    fun clearingTheSongsFiltersKeepsItsSort() = runTest {
        viewModel.uiState.test {
            viewModel.setSongSort(SongSortOrder.Album)
            viewModel.cycleFavoritesFilter(LibraryTab.Songs)
            viewModel.cycleDownloadedFilter(LibraryTab.Songs)

            viewModel.clearFilters(LibraryTab.Songs)
            val state = expectMostRecentItem()
            assertThat(state.songFilter).isEqualTo(SongFilter())
            assertThat(state.songSort).isEqualTo(SongSortOrder.Album)
        }
    }

    @Test
    fun offlineSongsTabShowsDownloadedSongsWithTheChipOn() = runTest {
        library.downloadedSongsState.value = listOf(TestData.song("s1"))
        viewModel.cycleDownloadedFilter(LibraryTab.Songs)
        viewModel.cycleDownloadedFilter(LibraryTab.Songs)
        offlineMode.isOffline.value = true
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.songs.collect {} }
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.chipFilters(LibraryTab.Songs)?.shownDownloaded).isTrue()
            assertThat(state.songCount).isEqualTo(1)
        }
        assertThat(library.lastFilter).isEqualTo(SongFilter(downloaded = true))
    }

    @Test
    fun songFiltersThatMatchNothingAreEmptyNotLoading() = runTest {
        sync.progress.value = SyncProgress.Running(SyncPhase.Songs, 1, 2)
        viewModel.uiState.test {
            viewModel.cycleFavoritesFilter(LibraryTab.Songs)
            val state = expectMostRecentItem()
            assertThat(state.songCount).isEqualTo(0)
            assertThat(state.screenState(LibraryTab.Songs)).isEqualTo(ScreenState.Empty)
        }
    }

    @Test
    fun albumsSortAndFiltersDoNotLeakIntoArtists() = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.albums.collect {} }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.artists.collect {} }
        viewModel.uiState.test {
            viewModel.setAlbumSort(AlbumSortOrder.Year)
            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            viewModel.cycleDownloadedFilter(LibraryTab.Albums)
            viewModel.setGenreFilter("Rock")
            val state = expectMostRecentItem()
            assertThat(state.artistSort).isEqualTo(ArtistSortOrder.Name)
            assertThat(state.artistFilter).isEqualTo(ArtistFilter())
            assertThat(state.artistCount).isEqualTo(1)
        }
        assertThat(artists.lastSort).isEqualTo(ArtistSortOrder.Name)
        assertThat(artists.lastFilter).isEqualTo(ArtistFilter())
    }

    @Test
    fun artistsSortAndFiltersDoNotLeakIntoAlbums() = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.albums.collect {} }
        viewModel.uiState.test {
            viewModel.setArtistSort(ArtistSortOrder.RecentlyAdded)
            viewModel.cycleFavoritesFilter(LibraryTab.Artists)
            viewModel.cycleDownloadedFilter(LibraryTab.Artists)
            val state = expectMostRecentItem()
            assertThat(state.albumSort).isEqualTo(AlbumSortOrder.RecentlyAdded)
            assertThat(state.albumFilter).isEqualTo(AlbumFilter())
            assertThat(state.albumCount).isEqualTo(3)
        }
        assertThat(albums.lastFilter).isEqualTo(AlbumFilter())
    }

    @Test
    fun clearingFiltersClearsOnlyThatTabs() = runTest {
        viewModel.uiState.test {
            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            viewModel.cycleFavoritesFilter(LibraryTab.Artists)
            viewModel.cycleDownloadedFilter(LibraryTab.Artists)

            viewModel.clearFilters(LibraryTab.Artists)
            val state = expectMostRecentItem()
            assertThat(state.artistFilter).isEqualTo(ArtistFilter())
            assertThat(state.albumFilter).isEqualTo(AlbumFilter(starred = true))
        }
    }

    @Test
    fun offlineArtistsTabShowsDownloadedArtistsWithTheChipOn() = runTest {
        artists.downloadedArtistIds = setOf("artist-1")
        artists.artistsState.value = listOf(TestData.artist("artist-1"), TestData.artist("artist-2"))
        viewModel.cycleDownloadedFilter(LibraryTab.Artists)
        viewModel.cycleDownloadedFilter(LibraryTab.Artists)
        offlineMode.isOffline.value = true
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.artists.collect {} }
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.chipFilters(LibraryTab.Artists)?.shownDownloaded).isTrue()
            assertThat(state.artistCount).isEqualTo(1)
        }
        assertThat(artists.lastFilter).isEqualTo(ArtistFilter(downloaded = true))
    }

    @Test
    fun eachTabsSortAndFiltersSurviveInSavedState() = runTest {
        viewModel.setAlbumSort(AlbumSortOrder.Name)
        viewModel.setArtistSort(ArtistSortOrder.AlbumCount)
        viewModel.cycleFavoritesFilter(LibraryTab.Artists)
        viewModel.cycleDownloadedFilter(LibraryTab.Albums)
        viewModel.setSongSort(SongSortOrder.RecentlyAdded)
        viewModel.cycleDownloadedFilter(LibraryTab.Songs)

        val restored = newViewModel()
        restored.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.albumSort).isEqualTo(AlbumSortOrder.Name)
            assertThat(state.albumFilter).isEqualTo(AlbumFilter(downloaded = true))
            assertThat(state.artistSort).isEqualTo(ArtistSortOrder.AlbumCount)
            assertThat(state.artistFilter).isEqualTo(ArtistFilter(starred = true))
            assertThat(state.songSort).isEqualTo(SongSortOrder.RecentlyAdded)
            assertThat(state.songFilter).isEqualTo(SongFilter(downloaded = true))
        }
    }

    @Test
    fun tabsWithoutFilterChipsHaveNone() {
        val state = LibraryUiState()
        assertThat(LibraryTab.entries.filter { state.chipFilters(it) != null })
            .containsExactly(LibraryTab.Albums, LibraryTab.Artists, LibraryTab.Songs)
    }

    @Test
    fun shuffleAllPlaysEverySongOfTheLibraryInATracksShuffle() = runTest {
        viewModel.shuffleAll()

        assertThat(play.albumPlays.single()).isEqualTo(
            RecordingPlayIntents.AlbumPlay(
                AlbumPlayRequest(AlbumSource.Library, AlbumOrder.ShuffledTracks),
                QueueSource.Library(shuffled = true),
            ),
        )
    }

    @Test
    fun offlineShuffleAllPlaysTheDownloadedSongsShuffled() = runTest {
        offlineMode.isOffline.value = true
        library.downloadedSongsState.value = listOf(TestData.song("s2"), TestData.song("s7"))

        viewModel.shuffleAll()

        assertThat(play.intents).containsExactly(
            RecordingPlayIntents.Play(
                PlayTarget.Songs(listOf(TestData.song("s2"), TestData.song("s7"))),
                QueuePlacement.Replace,
                shuffle = true,
                source = QueueSource.Library(shuffled = true),
            ),
        )
    }

    @Test
    fun shuffleAllIsHiddenWhenTheSongsTabHasNoSongs() = runTest {
        // Before Room has counted, so it doesn't flash.
        assertThat(LibraryUiState().showsShuffleAll).isFalse()

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().showsShuffleAll).isTrue()

            offlineMode.isOffline.value = true
            assertThat(expectMostRecentItem().showsShuffleAll).isFalse()

            library.downloadedSongsState.value = listOf(TestData.song("s2"))
            assertThat(expectMostRecentItem().showsShuffleAll).isTrue()
        }
    }

    @Test
    fun shuffleAllIgnoresSongFiltersThatMatchNothing() = runTest {
        viewModel.uiState.test {
            // No song is a favorite, but Shuffle all plays every song anyway.
            viewModel.cycleFavoritesFilter(LibraryTab.Songs)
            var state = expectMostRecentItem()
            assertThat(state.songCount).isEqualTo(0)
            assertThat(state.showsShuffleAll).isTrue()

            // Offline it plays every downloaded song.
            library.downloadedSongsState.value = listOf(TestData.song("s2"))
            offlineMode.isOffline.value = true
            state = expectMostRecentItem()
            assertThat(state.songCount).isEqualTo(0)
            assertThat(state.showsShuffleAll).isTrue()
        }
    }

    @Test
    fun aGenresShuffleAlbumsNamesTheGenre() = runTest {
        viewModel.shuffleGenreAlbums("Jazz")

        assertThat(play.albumPlays.single().source).isEqualTo(QueueSource.Genre("Jazz"))
    }

    @Test
    fun menusGoThroughTheMediaActionHandler() = runTest {
        albums.songsByAlbum.value = mapOf("1" to listOf(TestData.song("a"), TestData.song("b")))
        var added: List<String>? = null
        val host = MediaActionHost(onAddToPlaylist = { added = it })

        viewModel.onAlbumAction(albums.albumsState.value.first(), MediaAction.Play, host)
        viewModel.onAlbumAction(albums.albumsState.value.first(), MediaAction.AddToPlaylist, host)
        // The same rule as the artist page: top songs, else the artist's albums.
        viewModel.onArtistAction(TestData.artist("x"), MediaAction.Play, host)
        viewModel.onSongAction(TestData.song("s1"), MediaAction.Download, host)

        assertThat(play.plays.map { it.target }).containsExactly(PlayTarget.Album("1"), PlayTarget.Artist("x"))
            .inOrder()
        assertThat(added).containsExactly("a", "b").inOrder()
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Song to "s1")
    }

    @Test
    fun menuFailureShowsMessageUntilShown() = runTest {
        stars.error = RemoteException.Unreachable(IOException("offline"))
        viewModel.uiState.test {
            viewModel.onAlbumAction(albums.albumsState.value[1], MediaAction.Favorite, MediaActionHost())
            assertThat(
                expectMostRecentItem().message,
            ).isEqualTo(LibraryMessage.ActionFailed(MediaActionFailure.FavoriteFailed))

            viewModel.onMessageDismiss()
            assertThat(expectMostRecentItem().message).isNull()
        }
    }

    @Test
    fun playAlbumsQueuesTheAlbumsViewAsSortedAndFiltered() = runTest {
        viewModel.setAlbumSort(AlbumSortOrder.Name)
        viewModel.cycleFavoritesFilter(LibraryTab.Albums)
        viewModel.setGenreFilter("Rock")

        viewModel.playAlbums(AlbumOrder.InOrder, QueuePlacement.Replace)
        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.End)

        val view = AlbumSource.Albums(AlbumSortOrder.Name, AlbumFilter(starred = true, genre = "Rock"))
        assertThat(play.albumPlays).containsExactly(
            RecordingPlayIntents.AlbumPlay(
                AlbumPlayRequest(view, AlbumOrder.InOrder, QueuePlacement.Replace),
                QueueSource.Library(shuffled = false),
            ),
            RecordingPlayIntents.AlbumPlay(
                AlbumPlayRequest(view, AlbumOrder.Shuffled, QueuePlacement.End),
                QueueSource.Library(shuffled = true),
            ),
        ).inOrder()
    }

    @Test
    fun playAlbumsOfflineIsLimitedToDownloadedAlbumsLikeTheView() = runTest {
        offlineMode.isOffline.value = true

        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.Next)

        val source = play.albumRequests.single().source as AlbumSource.Albums
        assertThat(source.sort).isEqualTo(AlbumSortOrder.RecentlyAdded)
        assertThat(source.filter).isEqualTo(AlbumFilter(downloaded = true))
    }

    @Test
    fun selectionKeepsThePickOrderAndEndsWithItsLastPick() = runTest {
        val (one, two, three) = albums.albumsState.value
        viewModel.uiState.test {
            viewModel.toggleSelected(LibraryItem.OfAlbum(three))
            viewModel.toggleSelected(LibraryItem.OfAlbum(one))
            viewModel.toggleSelected(LibraryItem.OfAlbum(two))
            viewModel.toggleSelected(LibraryItem.OfAlbum(one))
            val picked = expectMostRecentItem().selection as LibrarySelection.Albums
            assertThat(picked.albums.map { it.id }).containsExactly("3", "2").inOrder()

            viewModel.toggleSelected(LibraryItem.OfAlbum(three))
            viewModel.toggleSelected(LibraryItem.OfAlbum(two))
            assertThat(expectMostRecentItem().selection).isNull()
        }
    }

    @Test
    fun aDragPicksAndDropsWithoutTogglingWhatIsAlreadySo() = runTest {
        val (one, two) = albums.albumsState.value
        viewModel.uiState.test {
            viewModel.toggleSelected(LibraryItem.OfAlbum(one))
            viewModel.setPicked(LibraryItem.OfAlbum(two), picked = true)
            viewModel.setPicked(LibraryItem.OfAlbum(two), picked = true)
            viewModel.setPicked(LibraryItem.OfAlbum(one), picked = true)
            val picked = expectMostRecentItem().selection as LibrarySelection.Albums
            assertThat(picked.albums.map { it.id }).containsExactly("1", "2").inOrder()

            viewModel.setPicked(LibraryItem.OfAlbum(one), picked = false)
            viewModel.setPicked(LibraryItem.OfAlbum(one), picked = false)
            viewModel.setPicked(LibraryItem.OfAlbum(two), picked = false)
            assertThat(expectMostRecentItem().selection).isNull()
        }
    }

    @Test
    fun aPinchKeepsEachGridsSizeInPreferences() = runTest {
        viewModel.uiState.test {
            viewModel.setGridSize(LibraryTab.Albums, GridSize(1))
            viewModel.setGridSize(LibraryTab.Artists, GridSize(-1))
            val state = expectMostRecentItem()
            assertThat(state.albumGridSize).isEqualTo(GridSize(1))
            assertThat(state.artistGridSize).isEqualTo(GridSize(-1))
        }
        assertThat(preferences.preferences.value.albumGridSize).isEqualTo(GridSize(1))
        assertThat(preferences.preferences.value.artistGridSize).isEqualTo(GridSize(-1))
    }

    @Test
    fun anotherTabEndsTheSelection() = runTest {
        viewModel.uiState.test {
            viewModel.selectTab(LibraryTab.Songs)
            viewModel.toggleSelected(LibraryItem.OfSong(TestData.song("s1")))
            // Tapping the tab it is on keeps it.
            viewModel.selectTab(LibraryTab.Songs)
            assertThat(expectMostRecentItem().selection).isNotNull()

            viewModel.selectTab(LibraryTab.Albums)
            assertThat(expectMostRecentItem().selection).isNull()
        }
    }

    @Test
    fun aNewFilterKeepsThePicksEvenOnesItHides() = runTest {
        val (starred, plain) = albums.albumsState.value
        viewModel.uiState.test {
            viewModel.toggleSelected(LibraryItem.OfAlbum(plain))
            viewModel.toggleSelected(LibraryItem.OfAlbum(starred))
            viewModel.cycleFavoritesFilter(LibraryTab.Albums)
            val state = expectMostRecentItem()
            assertThat(state.albumCount).isEqualTo(1)
            assertThat(state.selection?.ids).containsExactly(plain.id, starred.id)
        }
    }

    @Test
    fun aSelectionActionRunsOnEveryPickInOrderAndEndsTheSelection() = runTest {
        val (one, _, three) = albums.albumsState.value
        viewModel.uiState.test {
            viewModel.toggleSelected(LibraryItem.OfAlbum(three))
            viewModel.toggleSelected(LibraryItem.OfAlbum(one))
            viewModel.onSelectionAction(MediaAction.PlayNext, MediaActionHost())

            assertThat(expectMostRecentItem().selection).isNull()
        }
        assertThat(play.plays.single()).isEqualTo(
            RecordingPlayIntents.Play(
                PlayTarget.Each(listOf(PlayTarget.Album("3"), PlayTarget.Album("1"))),
                QueuePlacement.Next,
                shuffle = false,
            ),
        )
    }

    @Test
    fun selectionMenuUndoesFavoriteAndDownloadOnlyOnceEveryPickHasThem() {
        val (starred, plain) = albums.albumsState.value
        val pinned = persistentMapOf(starred.id to DownloadStatus.Downloaded)

        val one = LibraryUiState(pinnedAlbums = pinned, selection = LibrarySelection.Albums(persistentListOf(starred)))
        assertThat(one.selectionActions).containsAtLeast(MediaAction.Unfavorite, MediaAction.RemoveDownload)

        val both = one.copy(selection = LibrarySelection.Albums(persistentListOf(starred, plain)))
        assertThat(both.selectionActions).containsAtLeast(MediaAction.Favorite, MediaAction.Download)
        assertThat(both.selectionActions).containsNoneOf(MediaAction.Unfavorite, MediaAction.RemoveDownload)
    }

    @Test
    fun refreshRequestsAnIncrementalSync() = runTest {
        viewModel.refresh()

        assertThat(sync.requests).containsExactly(false)
    }

    @Test
    fun aFailedSyncWithNoDataIsAnErrorNotEmpty() = runTest {
        emptyLibrary()
        sync.progress.value = SyncProgress.Failed("Connection refused")

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            for (tab in BROWSE_TABS) {
                assertThat(state.screenState(tab)).isEqualTo(ScreenState.Error(ScreenState.Cause.Server))
            }
        }
    }

    @Test
    fun aTabWithDataStaysContentWhenTheSyncFailed() = runTest {
        sync.progress.value = SyncProgress.Failed("Connection refused")

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            for (tab in BROWSE_TABS) {
                assertThat(state.screenState(tab)).isEqualTo(ScreenState.Content)
            }
        }
    }

    @Test
    fun anEmptyTabIsLoadingWhileTheSyncRunsAndEmptyOnceItSucceeded() = runTest {
        emptyLibrary()
        sync.progress.value = SyncProgress.Pending

        viewModel.uiState.test {
            val pending = expectMostRecentItem()
            for (tab in BROWSE_TABS) {
                assertThat(pending.screenState(tab)).isEqualTo(ScreenState.Loading)
            }

            sync.progress.value = SyncProgress.Running(SyncPhase.Albums, completed = 0, total = 0)
            assertThat(expectMostRecentItem().screenState(LibraryTab.Albums)).isEqualTo(ScreenState.Loading)

            sync.progress.value = SyncProgress.Succeeded
            val synced = expectMostRecentItem()
            for (tab in BROWSE_TABS) {
                assertThat(synced.screenState(tab)).isEqualTo(ScreenState.Empty)
            }
        }
    }

    @Test
    fun offlineWithNothingDownloadedIsEmptyNotAnError() = runTest {
        sync.progress.value = SyncProgress.Failed("Unreachable")
        offlineMode.isOffline.value = true

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.screenState(LibraryTab.Albums)).isEqualTo(ScreenState.Empty)
            assertThat(state.screenState(LibraryTab.Artists)).isEqualTo(ScreenState.Empty)
            assertThat(state.screenState(LibraryTab.Songs)).isEqualTo(ScreenState.Empty)
        }
    }

    @Test
    fun artistsAFilterLeavesWithoutMatchesAreEmptyNotAnError() = runTest {
        sync.progress.value = SyncProgress.Failed("Connection refused")

        viewModel.uiState.test {
            viewModel.cycleFavoritesFilter(LibraryTab.Artists)

            val state = expectMostRecentItem()
            assertThat(state.artistCount).isEqualTo(0)
            assertThat(state.screenState(LibraryTab.Artists)).isEqualTo(ScreenState.Empty)
        }
    }

    @Test
    fun albumsAFilterLeavesWithoutMatchesAreEmptyNotAnError() = runTest {
        albums.albumsState.value = listOf(TestData.album("1"))
        sync.progress.value = SyncProgress.Failed("Connection refused")

        viewModel.uiState.test {
            viewModel.cycleFavoritesFilter(LibraryTab.Albums)

            assertThat(expectMostRecentItem().screenState(LibraryTab.Albums)).isEqualTo(ScreenState.Empty)
        }
    }

    private fun emptyLibrary() {
        albums.albumsState.value = emptyList()
        artists.artistsState.value = emptyList()
        library.songsState.value = emptyList()
        library.genresState.value = emptyList()
    }

    private companion object {
        /** The tabs whose screen state the Library decides; Playlists and Downloads have their own. */
        val BROWSE_TABS = listOf(LibraryTab.Albums, LibraryTab.Artists, LibraryTab.Songs, LibraryTab.Genres)
    }
}
