package com.quantumff.segue.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeLibrarySyncRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import java.io.IOException
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

    private fun viewModel() = HomeViewModel(albums, playlists, library, servers, sync, stars)

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
            assertThat(state.isEmpty).isFalse()
        }
    }

    @Test
    fun emptyLibraryWhileSyncingIsReportedAsSyncing() = runTest {
        sync.progress.value = SyncProgress.Running(SyncPhase.Albums, 1, 10)

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isEmpty).isTrue()
            assertThat(state.isSyncing).isTrue()
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun unreachableServerMarksOffline() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("timeout"))

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isOffline).isTrue()
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun playAlbumLoadsSongsThenRaisesThePlayIntent() = runTest {
        val album = TestData.album("a1")
        val songs = listOf(TestData.song("s1"), TestData.song("s2"))
        albums.songsByAlbum.value = mapOf("a1" to songs)
        val played = mutableListOf<Triple<List<Song>, Int, Boolean>>()
        val actions = PlayActions(
            onPlaySongs = { s, i, shuffle -> played += Triple(s, i, shuffle) },
            onShuffleAlbums = {},
            onPlayNext = {},
            onAddToQueue = {},
            onStartInstantMix = {},
        )
        val viewModel = viewModel()

        dispatchAlbumAction(album, MediaAction.Shuffle, actions, viewModel::withAlbumSongs, viewModel::toggleFavorite, {
        }, {})

        assertThat(played.single()).isEqualTo(Triple(songs, 0, true))
    }

    @Test
    fun shuffleArtistAlbumsUsesTheArtistSource() = runTest {
        val sources = mutableListOf<AlbumShuffleSource>()
        val actions = PlayActions(
            onPlaySongs = { _, _, _ -> },
            onShuffleAlbums = { sources += it },
            onPlayNext = {},
            onAddToQueue = {},
            onStartInstantMix = {},
        )
        val viewModel = viewModel()

        dispatchAlbumAction(
            TestData.album("a1", artistId = "ar1"),
            MediaAction.ShuffleArtistAlbums,
            actions,
            viewModel::withAlbumSongs,
            viewModel::toggleFavorite,
            {},
            {},
        )

        assertThat(sources.single()).isEqualTo(AlbumShuffleSource.Artist("ar1"))
    }

    @Test
    fun albumWithoutSongsShowsAMessage() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            viewModel.withAlbumSongs(TestData.album("missing")) { error("must not play") }
            assertThat(expectMostRecentItem().message).isEqualTo(HomeMessage.PlaybackLoadFailed)
            viewModel.messageShown()
            assertThat(awaitItem().message).isNull()
        }
    }

    @Test
    fun favoriteTogglesAndFailureShowsAMessage() = runTest {
        val viewModel = viewModel()
        viewModel.toggleFavorite(TestData.album("a1"))
        assertThat(stars.starred[MediaKind.Album to "a1"]).isTrue()

        stars.error = RemoteException.Unreachable(IOException("offline"))
        viewModel.uiState.test {
            viewModel.toggleFavorite(TestData.album("a1", starredAt = 1L))
            assertThat(expectMostRecentItem().message).isEqualTo(HomeMessage.FavoriteFailed)
        }
    }

    @Test
    fun retrySyncRequestsAnIncrementalSync() = runTest {
        viewModel().retrySync()

        assertThat(sync.requests).containsExactly(false)
    }
}
