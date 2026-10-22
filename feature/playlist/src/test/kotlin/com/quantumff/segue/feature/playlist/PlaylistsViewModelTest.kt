package com.quantumff.segue.feature.playlist

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PlaylistsViewModelTest {
    private val downloads = FakeDownloadRepository()
    private val offlineMode = FakeOfflineModeRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlists = FakePlaylistRepository()
    private val servers = FakeServerRepository().apply {
        serversState.value = listOf(TestData.server)
        activeId.value = TestData.server.id
    }

    private val play = RecordingPlayIntents()

    private fun viewModel() = PlaylistsViewModel(
        playlists,
        servers,
        offlineLibraryView(offlineMode, downloads, playlists = playlists),
        mediaActionHandler(play, downloads, playlists = playlists),
    )

    @Test
    fun showsPlaylistsAndServerName_andRefreshesOnEntry() = runTest {
        playlists.playlistsState.value = listOf(TestData.playlist("p1"), TestData.playlist("p2"))
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.playlists.map { it.id }).containsExactly("p1", "p2").inOrder()
            assertThat(state.serverName).isEqualTo(TestData.server.name)
        }
        assertThat(playlists.refreshAllCount).isEqualTo(1)
    }

    @Test
    fun createRenameAndDelete() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.create("Road trip")
        val created = viewModel.uiState.value.playlists.single()
        assertThat(created.name).isEqualTo("Road trip")

        viewModel.rename(created, "Night drive")
        assertThat(viewModel.uiState.value.playlists.single().name).isEqualTo("Night drive")

        viewModel.delete(viewModel.uiState.value.playlists.single())
        assertThat(playlists.deleted).containsExactly(created.id)
        assertThat(viewModel.uiState.value.playlists).isEmpty()
    }

    @Test
    fun failedWriteShowsMessageUntilShown() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        viewModel.create("Road trip")
        assertThat(viewModel.uiState.value.message).isEqualTo(PlaylistMessage.Failed)

        viewModel.onMessageDismiss()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun silentEntryRefreshButPullToRefreshReportsFailure() = runTest {
        playlists.error = RemoteException.Unreachable(IOException("offline"))
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertThat(viewModel.uiState.value.message).isNull()

        viewModel.refresh()

        assertThat(viewModel.uiState.value.message).isEqualTo(PlaylistMessage.Failed)
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun menuPlaysThePlaylistAndShufflesItsAlbums() = runTest {
        val viewModel = viewModel()
        val playlist = TestData.playlist("p1")

        viewModel.onPlaylistAction(playlist, MediaAction.Shuffle)
        viewModel.onPlaylistAction(playlist, MediaAction.ShuffleAlbums)

        val source = QueueSource.Playlist("p1", "Playlist p1")
        assertThat(play.plays.single()).isEqualTo(
            RecordingPlayIntents.Play(PlayTarget.Playlist("p1"), QueuePlacement.Replace, shuffle = true, source),
        )
        assertThat(play.albumRequests.single().source).isEqualTo(AlbumSource.Playlist("p1"))
    }

    @Test
    fun menuDownloadPinsThePlaylistAndRemoveUnpinsIt() = runTest {
        val viewModel = viewModel()
        val playlist = TestData.playlist("p1")

        viewModel.onPlaylistAction(playlist, MediaAction.Download)
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Playlist to "p1")

        viewModel.onPlaylistAction(playlist, MediaAction.RemoveDownload)
        assertThat(downloads.pins.value).isEmpty()
    }
}
