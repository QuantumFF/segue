package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.PlayingSong
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.FakePlayerStateSource
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.play.playerStateOf
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
        viewModel = LibraryViewModel(
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
    }

    @Test
    fun thePlayingSongsRowsAreMarkedAndFollowThePlayer() = runTest {
        val s1 = TestData.song("s1")
        val s2 = TestData.song("s2")
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().playing).isNull()

            player.state.value = playerStateOf("s2", isPlaying = true)
            var state = expectMostRecentItem()
            assertThat(state.playing).isEqualTo(PlayingSong("s2", isPlaying = true))
            assertThat(listOf(s1, s2).map(state::isCurrent)).containsExactly(false, true).inOrder()

            player.state.value = playerStateOf("s2", isPlaying = false)
            assertThat(expectMostRecentItem().playing).isEqualTo(PlayingSong("s2", isPlaying = false))

            player.state.value = playerStateOf("s1", isPlaying = true)
            state = expectMostRecentItem()
            assertThat(listOf(s1, s2).map(state::isCurrent)).containsExactly(true, false).inOrder()

            player.state.value = PlayerUiState()
            assertThat(expectMostRecentItem().playing).isNull()
        }
    }

    @Test
    fun downloadedChipFiltersAlbumsToDownloadedOnes() = runTest {
        albums.downloadedAlbumIds = setOf("2")
        viewModel.uiState.test {
            viewModel.cycleDownloadedFilter()
            val state = expectMostRecentItem()
            assertThat(state.filter.downloaded).isTrue()
            assertThat(state.albumCount).isEqualTo(1)

            viewModel.clearFilters()
            assertThat(expectMostRecentItem().filter.downloaded).isNull()
        }
    }

    @Test
    fun downloadedChipCyclesThroughOnlyExcludedAndOff() = runTest {
        albums.downloadedAlbumIds = setOf("2")
        viewModel.uiState.test {
            viewModel.cycleDownloadedFilter()
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)

            viewModel.cycleDownloadedFilter()
            val excluded = expectMostRecentItem()
            assertThat(excluded.filter.downloaded).isFalse()
            assertThat(excluded.shownDownloadedFilter).isFalse()
            assertThat(excluded.albumCount).isEqualTo(2)

            viewModel.cycleDownloadedFilter()
            val off = expectMostRecentItem()
            assertThat(off.filter.downloaded).isNull()
            assertThat(off.albumCount).isEqualTo(3)
        }
    }

    @Test
    fun offlineForcesTheDownloadedChipOnEvenWhenExcluded() = runTest {
        albums.downloadedAlbumIds = setOf("1")
        viewModel.cycleDownloadedFilter()
        viewModel.cycleDownloadedFilter()
        offlineMode.isOffline.value = true
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.shownDownloadedFilter).isTrue()
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
            assertThat(state.shownDownloadedFilter).isTrue()
            assertThat(state.albumCount).isEqualTo(1)
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
            viewModel.cycleFavoritesFilter()
            val state = expectMostRecentItem()
            assertThat(state.filter).isEqualTo(AlbumFilter(starred = true))
            assertThat(state.albumCount).isEqualTo(1)

            viewModel.cycleFavoritesFilter()
            val excluded = expectMostRecentItem()
            assertThat(excluded.filter).isEqualTo(AlbumFilter(starred = false))
            assertThat(excluded.albumCount).isEqualTo(2)

            viewModel.cycleFavoritesFilter()
            assertThat(expectMostRecentItem().filter).isEqualTo(AlbumFilter())
        }
    }

    @Test
    fun genreAndYearFiltersCombineAndClear() = runTest {
        viewModel.uiState.test {
            viewModel.setGenreFilter("Rock")
            viewModel.setYearFilter(2001)
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)

            viewModel.clearFilters()
            val cleared = expectMostRecentItem()
            assertThat(cleared.filter.isActive).isFalse()
            assertThat(cleared.albumCount).isEqualTo(3)
        }
    }

    @Test
    fun sortTabAndViewModeSurviveInSavedState() = runTest {
        viewModel.uiState.test {
            viewModel.setSort(AlbumSortOrder.Year)
            viewModel.selectTab(LibraryTab.Genres)
            viewModel.toggleViewMode()
            val state = expectMostRecentItem()
            assertThat(state.sort).isEqualTo(AlbumSortOrder.Year)
            assertThat(state.tab).isEqualTo(LibraryTab.Genres)
            assertThat(state.viewMode).isEqualTo(LibraryViewMode.List)
        }
        assertThat(savedState.get<LibraryTab>(LibraryViewModel.KEY_TAB)).isEqualTo(LibraryTab.Genres)
    }

    @Test
    fun shuffleAllPlaysEverySongOfTheLibraryInATracksShuffle() = runTest {
        viewModel.shuffleAll()

        assertThat(play.albumRequests.single())
            .isEqualTo(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.ShuffledTracks))
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
        viewModel.setSort(AlbumSortOrder.Name)
        viewModel.cycleFavoritesFilter()
        viewModel.setGenreFilter("Rock")

        viewModel.playAlbums(AlbumOrder.InOrder, QueuePlacement.Replace)
        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.End)

        val view = AlbumSource.Albums(AlbumSortOrder.Name, AlbumFilter(starred = true, genre = "Rock"))
        assertThat(play.albumRequests).containsExactly(
            AlbumPlayRequest(view, AlbumOrder.InOrder, QueuePlacement.Replace),
            AlbumPlayRequest(view, AlbumOrder.Shuffled, QueuePlacement.End),
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
            viewModel.cycleFavoritesFilter()
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
}
