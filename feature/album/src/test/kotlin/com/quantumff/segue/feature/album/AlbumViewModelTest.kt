package com.quantumff.segue.feature.album

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.DiscTitle
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AlbumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val stars = FakeStarRatingRepository()
    private val downloads = FakeDownloadRepository()
    private val offlineMode = FakeOfflineModeRepository()
    private val played = mutableListOf<Triple<List<Song>, Int, Boolean>>()
    private val shuffledAlbums = mutableListOf<AlbumShuffleSource>()
    private val instantMixes = mutableListOf<String>()
    private val playActions = PlayActions(
        onPlaySongs = { songs, start, shuffle -> played += Triple(songs, start, shuffle) },
        onShuffleAlbums = { shuffledAlbums += it },
        onPlayNext = {},
        onAddToQueue = {},
        onStartInstantMix = { instantMixes += it.id },
    )

    private val album = TestData.album("album-1", songCount = 3, genre = "Rock")
        .copy(discTitles = persistentListOf(DiscTitle(2, "Bonus")))
    private val songs = listOf(
        TestData.song("s1", track = 1, discNumber = 1),
        TestData.song("s2", track = 2, discNumber = 1),
        TestData.song("s3", track = 1, discNumber = 2),
    )

    private fun viewModel() = AlbumViewModel(
        savedStateHandle = SavedStateHandle(mapOf(AlbumViewModel.ALBUM_ID_KEY to "album-1")),
        albumRepository = albums,
        artistRepository = artists,
        starRatingRepository = stars,
        downloadRepository = downloads,
        offlineModeRepository = offlineMode,
    )

    private fun seed() {
        albums.albumsState.value = listOf(album)
        albums.songsByAlbum.value = mapOf("album-1" to songs)
    }

    @Test
    fun multiDiscAlbumIsGroupedWithDiscTitles() = runTest {
        seed()
        viewModel().uiState.test {
            val state = awaitItem() as AlbumUiState.Success
            assertThat(state.showDiscHeaders).isTrue()
            assertThat(state.discs.map { it.number }).containsExactly(1, 2).inOrder()
            assertThat(state.discs[1].title).isEqualTo("Bonus")
            assertThat(state.discs[1].tracks.single().index).isEqualTo(2)
        }
    }

    @Test
    fun singleDiscAlbumHasNoHeaders() {
        val discs = groupDiscs(album, listOf(TestData.song("a"), TestData.song("b")))

        assertThat(discs).hasSize(1)
        assertThat(discs.single().number).isEqualTo(1)
    }

    @Test
    fun metadataSumsSongsAndOffersArtistAndGenreShuffles() = runTest {
        seed()
        viewModel().uiState.test {
            val state = awaitItem() as AlbumUiState.Success
            assertThat(state.trackCount).isEqualTo(3)
            assertThat(state.durationSeconds).isEqualTo(600)
            assertThat(state.shuffleOptions.map { it.source }).containsExactly(
                AlbumShuffleSource.Artist("artist-1"),
                AlbumShuffleSource.Genre("Rock"),
            ).inOrder()
        }
    }

    @Test
    fun moreByArtistExcludesThisAlbum() = runTest {
        seed()
        artists.albumsByArtist.value = mapOf("artist-1" to listOf(album, TestData.album("album-2")))
        viewModel().uiState.test {
            val state = awaitItem() as AlbumUiState.Success
            assertThat(state.moreByArtist.map { it.id }).containsExactly("album-2")
        }
    }

    @Test
    fun refreshFailureWithCachedDataShowsOffline() = runTest {
        seed()
        albums.refreshError = RemoteException.Unreachable(IOException("offline"))
        viewModel().uiState.test {
            assertThat((awaitItem() as AlbumUiState.Success).isOffline).isTrue()
        }
    }

    @Test
    fun refreshFailureWithoutDataIsErrorAndNotFoundIsNotFound() = runTest {
        albums.refreshError = RemoteException.Unreachable(IOException("offline"))
        viewModel().uiState.test { assertThat(awaitItem()).isEqualTo(AlbumUiState.Error) }

        albums.refreshError = RemoteException.NotFound(IOException("gone"))
        viewModel().uiState.test { assertThat(awaitItem()).isEqualTo(AlbumUiState.NotFound) }
    }

    @Test
    fun playAndShuffleSendTheAlbumInTrackOrder() = runTest {
        seed()
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.play(playActions, startIndex = 2)
        viewModel.play(playActions, shuffle = true)

        assertThat(played.map { it.second to it.third }).containsExactly(2 to false, 0 to true).inOrder()
        assertThat(played.first().first.map { it.id }).containsExactly("s1", "s2", "s3").inOrder()
    }

    @Test
    fun shuffleAlbumsByArtist() = runTest {
        viewModel().shuffleAlbums(playActions, ShuffleAlbumsOption.ByArtist("artist-1", "Artist 1"))

        assertThat(shuffledAlbums).containsExactly(AlbumShuffleSource.Artist("artist-1"))
    }

    @Test
    fun toggleStarStarsTheAlbumAndReportsFailure() = runTest {
        seed()
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.toggleAlbumStar()
            assertThat(stars.starred[MediaKind.Album to "album-1"]).isTrue()

            stars.error = RemoteException.Unreachable(IOException("offline"))
            viewModel.toggleSongStar(songs.first())
            assertThat((awaitItem() as AlbumUiState.Success).message).isEqualTo(AlbumMessage.FavoriteFailed)

            viewModel.onMessageDismiss()
            assertThat((awaitItem() as AlbumUiState.Success).message).isNull()
        }
    }

    @Test
    fun menuActionsRouteToPlayIntents() {
        val state = AlbumUiState.Success(
            album = album,
            songs = persistentListOf(*songs.toTypedArray()),
            discs = groupDiscs(album, songs),
            moreByArtist = persistentListOf(),
        )
        val addedToPlaylist = mutableListOf<List<String>>()

        handleAlbumAction(MediaAction.ShuffleArtistAlbums, state, playActions, { _, _ -> }, {}, {}, {}, {})
        handleSongAction(MediaAction.InstantMix, songs[1], playActions, {}, {}, {}, {})
        handleSongAction(MediaAction.AddToPlaylist, songs[1], playActions, {}, {}, { addedToPlaylist += it }, {})

        assertThat(shuffledAlbums).containsExactly(AlbumShuffleSource.Artist("artist-1"))
        assertThat(instantMixes).containsExactly("s2")
        assertThat(addedToPlaylist).containsExactly(listOf("s2"))
    }

    @Test
    fun downloadPinsTheAlbumAndRemoveDownloadUnpinsIt() = runTest {
        seed()
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertThat((awaitItem() as AlbumUiState.Success).download).isNull()

            viewModel.download()
            assertThat((awaitItem() as AlbumUiState.Success).download).isEqualTo(DownloadStatus.Queued)
            assertThat(downloads.pins.value.keys).containsExactly(PinKind.Album to "album-1")

            viewModel.removeDownload()
            assertThat((awaitItem() as AlbumUiState.Success).download).isNull()
        }
    }

    @Test
    fun offlineModeDisablesSongsThatAreNotDownloaded() = runTest {
        seed()
        downloads.songDownloads.value = persistentMapOf("s1" to SongDownload("s1", DownloadState.Completed, 1f))
        offlineMode.isOffline.value = true
        viewModel().uiState.test {
            val state = awaitItem() as AlbumUiState.Success
            assertThat(songs.map(state::isAvailable)).containsExactly(true, false, false).inOrder()
        }
    }
}
