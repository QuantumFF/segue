package com.quantumff.segue.feature.playlist

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
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

    private fun viewModel() = PlaylistsViewModel(playlists, servers, downloads, offlineMode)

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
    fun loadSongsUsesCachedSongsForPlayIntents() = runTest {
        val songs = listOf(TestData.song("s1"), TestData.song("s2"))
        playlists.songsByPlaylist.value = mapOf("p1" to songs)
        val played = mutableListOf<Triple<List<Song>, Int, Boolean>>()
        val shuffled = mutableListOf<AlbumShuffleSource>()
        val actions = PlayActions(
            onPlaySongs = { s, index, shuffle -> played += Triple(s, index, shuffle) },
            onShuffleAlbums = { shuffled += it },
            onPlayNext = {},
            onAddToQueue = {},
            onStartInstantMix = {},
        )

        viewModel().loadSongs("p1") { actions.onPlaySongs(it, 0, true) }
        actions.onShuffleAlbums(AlbumShuffleSource.Playlist("p1"))

        assertThat(played).containsExactly(Triple(songs, 0, true))
        assertThat(shuffled).containsExactly(AlbumShuffleSource.Playlist("p1"))
        assertThat(playlists.refreshed).isEmpty()
    }

    @Test
    fun loadSongsRefreshesFirstWhenNotCached() = runTest {
        val loaded = mutableListOf<List<Song>>()

        viewModel().loadSongs("p1") { loaded += it }

        assertThat(playlists.refreshed).containsExactly("p1")
        // Still empty after the refresh: nothing to play, and no callback.
        assertThat(loaded).isEmpty()
    }

    @Test
    fun offlineListsOnlyPinnedPlaylistsWithTheirStatus() = runTest {
        playlists.playlistsState.value = listOf(TestData.playlist("p1"), TestData.playlist("p2"))
        downloads.overview.value = DownloadOverview(
            pins = persistentListOf(
                PinnedDownload(PinKind.Playlist, "p2", "Playlist p2", null, 2, 2, 0, 0, 1f, 10),
            ),
        )
        offlineMode.isOffline.value = true

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.playlists.map { it.id }).containsExactly("p2")
            assertThat(state.downloads).containsExactly("p2", DownloadStatus.Downloaded)
        }
    }
}
