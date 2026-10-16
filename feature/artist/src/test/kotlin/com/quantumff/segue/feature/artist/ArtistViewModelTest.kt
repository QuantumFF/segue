package com.quantumff.segue.feature.artist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.ArtistInfo
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.mediaActionHandler
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
    private val stars = FakeStarRatingRepository()
    private val play = RecordingPlayIntents()

    private val ownAlbum = TestData.album("own", artistId = "artist-1", songCount = 10)
    private val guestAlbum = TestData.album("guest", artistId = "someone-else", songCount = 12)

    private fun viewModel() = ArtistViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ArtistViewModel.ARTIST_ID_KEY to "artist-1")),
        artistRepository = artists,
        starRatingRepository = stars,
        downloadRepository = downloads,
        offlineView = offlineLibraryView(offlineMode, downloads),
        playIntents = play,
        mediaActions = mediaActionHandler(play, downloads, stars, artists = artists),
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

        viewModel.playAlbums(AlbumOrder.InOrder, QueuePlacement.Replace)
        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.Next)
        viewModel.playAlbums(AlbumOrder.InOrder, QueuePlacement.End)

        val artist = AlbumSource.Artist("artist-1")
        assertThat(play.albumRequests).containsExactly(
            AlbumPlayRequest(artist, AlbumOrder.InOrder, QueuePlacement.Replace),
            AlbumPlayRequest(artist, AlbumOrder.Shuffled, QueuePlacement.Next),
            AlbumPlayRequest(artist, AlbumOrder.InOrder, QueuePlacement.End),
        ).inOrder()
    }

    @Test
    fun playIsTheArtistTopSongsPlayFromATapAndShuffleAlbumsUsesTheArtist() = runTest {
        seed()
        val top = listOf(TestData.song("t1"), TestData.song("t2"))
        artists.topSongs = top
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.play(shuffle = true)
        viewModel.playTopSong(index = 1)
        viewModel.shuffleAlbums()

        assertThat(play.plays).containsExactly(
            RecordingPlayIntents.Play(PlayTarget.Artist("artist-1"), QueuePlacement.Replace, shuffle = true),
            RecordingPlayIntents.Play(PlayTarget.Songs(top, 1), QueuePlacement.Replace, shuffle = false),
        ).inOrder()
        assertThat(play.albumRequests)
            .containsExactly(AlbumPlayRequest(AlbumSource.Artist("artist-1"), AlbumOrder.Shuffled))
    }

    @Test
    fun instantMixIsSeededByTheArtist() = runTest {
        seed()
        val viewModel = viewModel()

        viewModel.startInstantMix()

        assertThat(play.mixes).containsExactly(InstantMixSeed.Artist("artist-1"))
        viewModel.uiState.test {
            assertThat((awaitItem() as ArtistUiState.Success).isPreparingPlayback).isFalse()
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
    fun menusGoThroughTheMediaActionHandler() = runTest {
        seed()
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val opened = mutableListOf<String>()

        viewModel.onSongAction(
            TestData.song("t1"),
            MediaAction.GoToAlbum,
            MediaActionHost(onAlbumClick = {
                opened += it
            }),
        )
        viewModel.onSongAction(TestData.song("t1"), MediaAction.Download, MediaActionHost())
        viewModel.onArtistAction(MediaAction.Play, MediaActionHost())

        assertThat(opened).containsExactly("album-1")
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Song to "t1")
        assertThat(play.plays.single().target).isEqualTo(PlayTarget.Artist("artist-1"))
    }
}
