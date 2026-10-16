package com.quantumff.segue.feature.artist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.ArtistInfo
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ArtistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val artists = FakeArtistRepository()
    private val albums = FakeAlbumRepository()
    private val stars = FakeStarRatingRepository()
    private val played = mutableListOf<Triple<List<Song>, Int, Boolean>>()
    private val albumRequests = mutableListOf<AlbumPlayRequest>()
    private val playActions = PlayActions(
        onPlaySongs = { songs, start, shuffle -> played += Triple(songs, start, shuffle) },
        onPlayAlbums = { albumRequests += it },
        onPlayNext = {},
        onAddToQueue = {},
        onStartInstantMix = {},
    )

    private val ownAlbum = TestData.album("own", artistId = "artist-1", songCount = 10)
    private val guestAlbum = TestData.album("guest", artistId = "someone-else", songCount = 12)

    private fun viewModel() = ArtistViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ArtistViewModel.ARTIST_ID_KEY to "artist-1")),
        artistRepository = artists,
        albumRepository = albums,
        starRatingRepository = stars,
        downloadRepository = downloads,
        offlineView = offlineLibraryView(offlineMode, downloads),
    )

    private val downloads = FakeDownloadRepository()
    private val offlineMode = FakeOfflineModeRepository()

    private fun seed() {
        artists.artistsState.value = listOf(TestData.artist("artist-1", albumCount = 1))
        artists.albumsByArtist.value = mapOf("artist-1" to listOf(ownAlbum, guestAlbum))
    }

    @Test
    fun albumsAreSplitIntoOwnAndAppearsOn() = runTest {
        seed()
        viewModel().uiState.test {
            val state = awaitItem() as ArtistUiState.Success
            assertThat(state.albums.map { it.id }).containsExactly("own")
            assertThat(state.appearsOn.map { it.id }).containsExactly("guest")
            assertThat(state.trackCount).isEqualTo(22)
        }
    }

    @Test
    fun topSongsAndInfoFillTheirSections() = runTest {
        seed()
        artists.topSongs = listOf(TestData.song("t1"), TestData.song("t2"))
        artists.info = ArtistInfo("Bio", persistentListOf(TestData.artist("artist-2")))
        viewModel().uiState.test {
            val state = awaitItem() as ArtistUiState.Success
            assertThat(state.topSongs.map { it.id }).containsExactly("t1", "t2").inOrder()
            assertThat(state.similarArtists.map { it.id }).containsExactly("artist-2")
            assertThat(state.biography).isEqualTo("Bio")
        }
    }

    @Test
    fun remoteFailuresHideOptionalSectionsAndMarkOffline() = runTest {
        seed()
        artists.info = ArtistInfo("Bio", persistentListOf(TestData.artist("artist-2")))
        artists.error = RemoteException.Unreachable(IOException("offline"))
        viewModel().uiState.test {
            val state = awaitItem() as ArtistUiState.Success
            assertThat(state.isOffline).isTrue()
            assertThat(state.similarArtists).isEmpty()
            assertThat(state.biography).isNull()
            assertThat(state.topSongs).isEmpty()
        }
    }

    @Test
    fun noCachedArtistAndFailedRefreshIsError() = runTest {
        artists.error = RemoteException.Unreachable(IOException("offline"))
        viewModel().uiState.test { assertThat(awaitItem()).isEqualTo(ArtistUiState.Error) }
    }

    @Test
    fun playAlbumsButtonsQueueTheArtistsAlbumsWithTheChosenOrderAndPlacement() = runTest {
        val viewModel = viewModel()

        viewModel.playAlbums(AlbumOrder.InOrder, QueuePlacement.Replace, playActions)
        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.Next, playActions)
        viewModel.playAlbums(AlbumOrder.InOrder, QueuePlacement.End, playActions)

        val artist = AlbumSource.Artist("artist-1")
        assertThat(albumRequests).containsExactly(
            AlbumPlayRequest(artist, AlbumOrder.InOrder, QueuePlacement.Replace),
            AlbumPlayRequest(artist, AlbumOrder.Shuffled, QueuePlacement.Next),
            AlbumPlayRequest(artist, AlbumOrder.InOrder, QueuePlacement.End),
        ).inOrder()
    }

    @Test
    fun playUsesTopSongsAndShuffleAlbumsUsesTheArtist() = runTest {
        seed()
        artists.topSongs = listOf(TestData.song("t1"), TestData.song("t2"))
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.play(playActions, shuffle = true)
        viewModel.playTopSong(playActions, index = 1)
        viewModel.shuffleAlbums(playActions)

        assertThat(played.map { Triple(it.first.map(Song::id), it.second, it.third) }).containsExactly(
            Triple(listOf("t1", "t2"), 0, true),
            Triple(listOf("t1", "t2"), 1, false),
        ).inOrder()
        assertThat(albumRequests).containsExactly(AlbumPlayRequest(AlbumSource.Artist("artist-1"), AlbumOrder.Shuffled))
    }

    @Test
    fun playWithoutTopSongsPlaysTheAlbums() = runTest {
        seed()
        albums.songsByAlbum.value = mapOf(
            "own" to listOf(TestData.song("o1")),
            "guest" to listOf(TestData.song("g1")),
        )
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.play(playActions, shuffle = false)

        assertThat(played.single().first.map { it.id }).containsExactly("o1", "g1").inOrder()
    }

    @Test
    fun instantMixPlaysSimilarSongsOrReportsNone() = runTest {
        seed()
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.startInstantMix(playActions)
            assertThat((expectMostRecentItem() as ArtistUiState.Success).message)
                .isEqualTo(ArtistMessage.NoSimilarSongs)

            artists.similarSongs = listOf(TestData.song("m1"))
            viewModel.startInstantMix(playActions)
            assertThat(played.single().first.map { it.id }).containsExactly("m1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleStarStarsTheArtist() = runTest {
        seed()
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.toggleArtistStar()
            assertThat(stars.starred[MediaKind.Artist to "artist-1"]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun topSongMenuGoesToAlbum() {
        val opened = mutableListOf<String>()
        handleTopSongAction(MediaAction.GoToAlbum, TestData.song("t1"), playActions, {}, { opened += it }, {}, {})

        assertThat(opened).containsExactly("album-1")
    }
}
