package com.quantumff.segue.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.ScreenState
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AlbumListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val albums = FakeAlbumRepository()
    private val downloads = FakeDownloadRepository()
    private val offline = FakeOfflineModeRepository()
    private val stars = FakeStarRatingRepository()
    private val play = RecordingPlayIntents()
    private val snackbars = Snackbars()

    private fun viewModel(type: AlbumListType = AlbumListType.Newest) = AlbumListViewModel(
        SavedStateHandle(mapOf(AlbumListViewModel.KEY_LIST_TYPE to type.name)),
        albums,
        offlineLibraryView(offline, downloads),
        mediaActionHandler(play, downloads, stars, albums, snackbars = snackbars),
        snackbars,
    )

    @Test
    fun onlineListsTheWholeServerList() = runTest {
        albums.lists.value = mapOf(AlbumListType.Newest to listOf(TestData.album("a1"), TestData.album("a2")))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().albums.map { it.id }).containsExactly("a1", "a2").inOrder()
        }
    }

    @Test
    fun offlineListsOnlyDownloadedAlbums() = runTest {
        offline.isOffline.value = true
        downloads.downloadedLibrary.value = DownloadedLibrary(albumIds = setOf("a1", "a3"))
        albums.lists.value = mapOf(AlbumListType.Newest to (1..3).map { TestData.album("a$it") })

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().albums.map { it.id }).containsExactly("a1", "a3").inOrder()
        }
    }

    @Test
    fun offlineWithNoneOfTheListDownloadedIsAnOfflineEmptyState() = runTest {
        offline.isOffline.value = true
        downloads.downloadedLibrary.value = DownloadedLibrary(albumIds = setOf("elsewhere"))
        albums.lists.value = mapOf(AlbumListType.Newest to listOf(TestData.album("a1"), TestData.album("a2")))

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.screenState).isEqualTo(ScreenState.Empty)
            assertThat(state.offlineStatus.isShowingDownloads).isTrue()
        }
    }

    @Test
    fun withAlbumsTheGridShows() = runTest {
        albums.lists.value = mapOf(AlbumListType.Newest to listOf(TestData.album("a1")))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screenState).isEqualTo(ScreenState.Content)
        }
    }

    @Test
    fun onlineTheStatusIsOnline() = runTest {
        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.Online)
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
    fun unreachableServerShowsTheCachedLibraryStatus() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("timeout"))

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.offlineStatus).isEqualTo(OfflineStatus.ShowingCachedLibrary)
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun refreshesTheServerListOnEntry() = runTest {
        viewModel(AlbumListType.Frequent)

        assertThat(albums.refreshedLists).containsExactly(AlbumListType.Frequent)
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
    fun refreshOnceBackOnlineAsksTheServer() = runTest {
        offline.isOffline.value = true
        val viewModel = viewModel()
        offline.isOffline.value = false

        viewModel.refresh()

        assertThat(albums.refreshedLists).containsExactly(AlbumListType.Newest)
        assertThat(snackbars.current.value).isNull()
    }
}
