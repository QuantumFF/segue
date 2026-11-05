package com.quantumff.segue.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.ScreenState
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeLibrarySyncRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val albums = FakeAlbumRepository()
    private val playlists = FakePlaylistRepository()
    private val library = FakeLibraryRepository()
    private val servers = FakeServerRepository()
    private val sync = FakeLibrarySyncRepository()
    private val stars = FakeStarRatingRepository()
    private val downloads = FakeDownloadRepository()
    private val offline = FakeOfflineModeRepository()
    private val play = RecordingPlayIntents()
    private val snackbars = Snackbars()

    private fun viewModel() = HomeViewModel(
        albums,
        library,
        servers,
        sync,
        offlineLibraryView(offline, downloads, playlists = playlists),
        play,
        mediaActionHandler(play, downloads, stars, albums, snackbars = snackbars),
        snackbars,
    )

    @Test
    fun refreshesEveryServerListOnEntry() = runTest {
        viewModel()

        assertThat(albums.refreshedLists).containsExactly(
            AlbumListType.Recent,
            AlbumListType.Newest,
            AlbumListType.Frequent,
            AlbumListType.Random,
        )
    }

    @Test
    fun randomListFeedsFeaturedThenExplore() = runTest {
        val random = (1..15).map { TestData.album("r$it") }
        albums.lists.value = mapOf(AlbumListType.Random to random)

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.featured.map { it.id }).isEqualTo(random.take(10).map { it.id })
            assertThat(state.explore.map { it.id }).isEqualTo(random.drop(10).map { it.id })
        }
    }

    @Test
    fun exploreRefreshKeepsFeaturedInPlace() = runTest {
        val first = (1..30).map { TestData.album("a$it") }
        albums.lists.value = mapOf(AlbumListType.Random to first)
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().featured.map { it.id }).isEqualTo(first.take(10).map { it.id })
            viewModel.refreshExplore()
            val reshuffled = (31..60).map { TestData.album("b$it") }
            albums.lists.value = mapOf(AlbumListType.Random to reshuffled)

            val state = expectMostRecentItem()
            assertThat(state.featured.map { it.id }).isEqualTo(first.take(10).map { it.id })
            assertThat(state.explore.map { it.id }).isEqualTo(reshuffled.take(20).map { it.id })
        }
    }

    @Test
    fun sectionsComeFromTheirLists() = runTest {
        albums.lists.value = mapOf(
            AlbumListType.Recent to listOf(TestData.album("recent")),
            AlbumListType.Newest to listOf(TestData.album("new")),
            AlbumListType.Frequent to listOf(TestData.album("often")),
        )
        albums.albumsState.value = listOf(TestData.album("fav", starredAt = 1L), TestData.album("plain"))
        servers.serversState.value = listOf(TestData.server)
        servers.activeId.value = TestData.server.id

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.recentlyPlayed.single().id).isEqualTo("recent")
            assertThat(state.recentlyAdded.single().id).isEqualTo("new")
            assertThat(state.mostPlayed.single().id).isEqualTo("often")
            assertThat(state.favorites.single().id).isEqualTo("fav")
            assertThat(state.serverName).isEqualTo(TestData.server.name)
            assertThat(state.screenState).isEqualTo(ScreenState.Content)
        }
    }

    @Test
    fun theFirstRefreshWithNothingInRoomIsLoading() = runTest {
        albums.listRefreshGate = CompletableDeferred()

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screenState).isEqualTo(ScreenState.Loading)
        }
    }

    @Test
    fun aReallyEmptyLibraryIsEmptyOnlyOnceTheFirstRefreshIsDone() = runTest {
        val gate = CompletableDeferred<Unit>()
        albums.listRefreshGate = gate
        val viewModel = viewModel()
        val states = mutableListOf<ScreenState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it.screenState }
        }

        assertThat(states).doesNotContain(ScreenState.Empty)
        assertThat(states.last()).isEqualTo(ScreenState.Loading)
        gate.complete(Unit)
        runCurrent()

        assertThat(states.last()).isEqualTo(ScreenState.Empty)
    }

    @Test
    fun aRefusingServerWithNothingInRoomIsAServerError() = runTest {
        albums.refreshError = RemoteException.ServerError("Internal error", IOException("500"))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screenState).isEqualTo(ScreenState.Error(ScreenState.Cause.Server))
        }
    }

    @Test
    fun anUnreachableServerWithNothingInRoomIsAServerError() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("timeout"))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screenState).isEqualTo(ScreenState.Error(ScreenState.Cause.Server))
        }
    }

    @Test
    fun offlineWithNothingDownloadedIsAnOfflineError() = runTest {
        offline.isOffline.value = true
        albums.lists.value = mapOf(AlbumListType.Random to listOf(TestData.album("r1")))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screenState).isEqualTo(ScreenState.Error(ScreenState.Cause.Offline))
        }
    }

    @Test
    fun offlineModeWithNothingDownloadedIsAnOfflineErrorToo() = runTest {
        offline.setOfflineMode(true)

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screenState).isEqualTo(ScreenState.Error(ScreenState.Cause.Offline))
        }
    }

    @Test
    fun emptyLibraryWhileSyncingIsReportedAsSyncing() = runTest {
        sync.progress.value = SyncProgress.Running(SyncPhase.Albums, 1, 10)

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.screenState).isEqualTo(ScreenState.Empty)
            assertThat(state.isSyncing).isTrue()
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun offlineCarouselsShowDownloadedAlbumsAndPinnedPlaylistsOnly() = runTest {
        offline.isOffline.value = true
        downloads.downloadedLibrary.value = DownloadedLibrary(albumIds = setOf("r2", "recent"))
        downloads.overview.value = DownloadOverview(
            pins = persistentListOf(PinnedDownload(PinKind.Playlist, "p2", "p2", null, 1, 1, 0, 0, 1f, 1)),
        )
        albums.lists.value = mapOf(
            AlbumListType.Random to (1..3).map { TestData.album("r$it") },
            AlbumListType.Recent to listOf(TestData.album("recent"), TestData.album("other")),
        )
        playlists.playlistsState.value = listOf(TestData.playlist("p1"), TestData.playlist("p2"))

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.featured.map { it.id }).containsExactly("r2")
            assertThat(state.recentlyPlayed.map { it.id }).containsExactly("recent")
            assertThat(state.playlists.map { it.id }).containsExactly("p2")
            assertThat(state.offlineStatus.isShowingDownloads).isTrue()
        }
    }

    @Test
    fun goingBackOnlineShowsTheWholeLibraryAgain() = runTest {
        offline.isOffline.value = true
        albums.lists.value = mapOf(AlbumListType.Random to listOf(TestData.album("r1")))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().featured).isEmpty()
            offline.isOffline.value = false
            assertThat(expectMostRecentItem().featured.map { it.id }).containsExactly("r1")
        }
    }

    @Test
    fun offlineShowsTheDownloadsStatus() = runTest {
        offline.isOffline.value = true

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingDownloads)
        }
    }

    @Test
    fun offlineModeShowsItsOwnStatus() = runTest {
        offline.setOfflineMode(true)

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingDownloadsInOfflineMode)
        }
    }

    @Test
    fun onlineTheStatusIsOnline() = runTest {
        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.Online)
        }
    }

    @Test
    fun offlineSkipsTheServerRefreshQuietlyOnEntry() = runTest {
        offline.isOffline.value = true

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().isRefreshing).isFalse()
        }
        assertThat(albums.refreshedLists).isEmpty()
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun refreshWhileOfflineSaysSoInsteadOfAskingTheServer() = runTest {
        offline.isOffline.value = true

        viewModel().refresh()

        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.offline)
        assertThat(albums.refreshedLists).isEmpty()
    }

    @Test
    fun refreshInOfflineModeSaysSoToo() = runTest {
        offline.setOfflineMode(true)

        viewModel().refresh()

        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.offline)
        assertThat(albums.refreshedLists).isEmpty()
    }

    @Test
    fun refreshOnceBackOnlineAsksTheServer() = runTest {
        offline.isOffline.value = true
        val viewModel = viewModel()
        offline.isOffline.value = false

        viewModel.refresh()

        assertThat(albums.refreshedLists).isNotEmpty()
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun unreachableServerShowsTheCachedLibraryStatus() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("timeout"))

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.offlineStatus).isEqualTo(OfflineStatus.ShowingCachedLibrary)
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun goingOfflineAfterAnUnreachableServerShowsDownloads() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("timeout"))
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingCachedLibrary)
            offline.isOffline.value = true
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingDownloads)
        }
    }

    @Test
    fun aCardsPlayButtonPlaysTheAlbum() = runTest {
        viewModel().playAlbum(TestData.album("a1"))

        assertThat(play.plays.single().target).isEqualTo(PlayTarget.Album("a1"))
        assertThat(play.plays.single().source).isEqualTo(QueueSource.Album("a1", "Album a1"))
    }

    @Test
    fun albumMenuDownloadPinsTheAlbum() = runTest {
        viewModel().onAlbumAction(TestData.album("a1"), MediaAction.Download, MediaActionHost())

        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Album to "a1")
    }

    @Test
    fun menuFailuresShowAMessage() = runTest {
        val viewModel = viewModel()
        stars.error = RemoteException.Unreachable(IOException("offline"))
        viewModel.uiState.test {
            viewModel.onAlbumAction(TestData.album("a1"), MediaAction.Favorite, MediaActionHost())
            assertThat(expectMostRecentItem().message).isEqualTo(MediaActionFailure.FavoriteFailed)
            viewModel.messageShown()
            assertThat(awaitItem().message).isNull()
        }
    }

    @Test
    fun retrySyncRequestsAnIncrementalSync() = runTest {
        viewModel().retrySync()

        assertThat(sync.requests).containsExactly(false)
    }
}
