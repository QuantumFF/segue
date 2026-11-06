package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class GenreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val albums = FakeAlbumRepository()
    private val downloads = FakeDownloadRepository()
    private val offline = FakeOfflineModeRepository()
    private val stars = FakeStarRatingRepository()
    private val play = RecordingPlayIntents()
    private val snackbars = Snackbars()

    private fun viewModel(genre: String = "Jazz") = GenreViewModel(
        SavedStateHandle(mapOf(GenreViewModel.KEY_GENRE to genre)),
        albums,
        offlineLibraryView(offline, downloads),
        play,
        mediaActionHandler(play, downloads, stars, albums, snackbars = snackbars),
        snackbars,
    )

    @Test
    fun countsOnlyTheGenresAlbums() = runTest {
        albums.albumsState.value = listOf(
            TestData.album("1", genre = "Jazz"),
            TestData.album("2", genre = "Rock"),
            TestData.album("3", genre = "Jazz"),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.genre).isEqualTo("Jazz")
            assertThat(state.albumCount).isEqualTo(2)
        }
    }

    @Test
    fun offlineTheGridShowsOnlyTheGenresDownloadedAlbums() = runTest {
        albums.albumsState.value = listOf(
            TestData.album("1", genre = "Jazz"),
            TestData.album("2", genre = "Rock"),
            TestData.album("3", genre = "Jazz"),
        )
        albums.downloadedAlbumIds = setOf("2", "3")
        offline.isOffline.value = true
        val viewModel = viewModel()

        viewModel.albums.first()
        assertThat(albums.lastFilter).isEqualTo(AlbumFilter(genre = "Jazz", downloaded = true))
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)
        }
    }

    @Test
    fun goingBackOnlineShowsTheWholeGenreAgain() = runTest {
        albums.albumsState.value = listOf(TestData.album("1", genre = "Jazz"), TestData.album("3", genre = "Jazz"))
        albums.downloadedAlbumIds = setOf("3")
        offline.isOffline.value = true
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().albumCount).isEqualTo(1)
            offline.isOffline.value = false
            assertThat(expectMostRecentItem().albumCount).isEqualTo(2)
        }
        viewModel.albums.first()
        assertThat(albums.lastFilter).isEqualTo(AlbumFilter(genre = "Jazz"))
    }

    @Test
    fun cardsCarryTheAlbumPinStatusesForBadges() = runTest {
        downloads.overview.value = DownloadOverview(
            pins = persistentListOf(
                PinnedDownload(PinKind.Album, "1", "One", null, 2, 2, 0, 0, 1f, 1),
                PinnedDownload(PinKind.Album, "3", "Three", null, 2, 0, 0, 0, 0f, 0),
                PinnedDownload(PinKind.Playlist, "p", "List", null, 1, 1, 0, 0, 1f, 1),
            ),
        )

        viewModel().uiState.test {
            val downloads = expectMostRecentItem().albumDownloads
            assertThat(downloads.keys).containsExactly("1", "3")
            assertThat(downloads["1"]).isEqualTo(DownloadStatus.Downloaded)
            assertThat(downloads["3"]).isEqualTo(DownloadStatus.Queued)
        }
    }

    @Test
    fun onlineTheGridHasNoOfflineStatus() = runTest {
        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.Online)
        }
    }

    @Test
    fun withoutANetworkTheGridSaysItShowsDownloads() = runTest {
        offline.isOffline.value = true

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingDownloads)
        }
    }

    @Test
    fun inOfflineModeTheGridSaysSo() = runTest {
        offline.setOfflineMode(true)

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().offlineStatus).isEqualTo(OfflineStatus.ShowingDownloadsInOfflineMode)
        }
    }

    @Test
    fun aGenreWithNoAlbumsIsEmpty() = runTest {
        albums.albumsState.value = listOf(TestData.album("2", genre = "Rock"))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screen).isEqualTo(ScreenState.Empty)
        }
    }

    @Test
    fun offlineAGenreWithNothingDownloadedIsEmpty() = runTest {
        albums.albumsState.value = listOf(TestData.album("1", genre = "Jazz"))
        offline.isOffline.value = true

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.screen).isEqualTo(ScreenState.Empty)
            assertThat(state.offlineStatus.isShowingDownloads).isTrue()
        }
    }

    @Test
    fun aGenreWithAlbumsHasContent() = runTest {
        albums.albumsState.value = listOf(TestData.album("1", genre = "Jazz"))

        viewModel().uiState.test {
            assertThat(expectMostRecentItem().screen).isEqualTo(ScreenState.Content)
        }
    }

    @Test
    fun theChipsRetryWhileOfflineSaysTheDeviceIsOffline() = runTest {
        offline.isOffline.value = true

        viewModel().retry()

        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.offline)
    }

    @Test
    fun theChipsRetryOnceBackOnlineSaysNothing() = runTest {
        viewModel().retry()

        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun theAlbumMenuGoesToTheArtist() = runTest {
        val opened = mutableListOf<String>()

        viewModel().onAlbumAction(
            TestData.album("1", genre = "Jazz").copy(artistId = "ar1"),
            MediaAction.GoToArtist,
            MediaActionHost(onArtistClick = { opened += it }),
        )

        assertThat(opened).containsExactly("ar1")
    }

    @Test
    fun theAlbumMenuDownloadPinsTheAlbum() = runTest {
        viewModel().onAlbumAction(TestData.album("1", genre = "Jazz"), MediaAction.Download, MediaActionHost())

        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Album to "1")
    }

    @Test
    fun albumMenuFailuresShowAMessage() = runTest {
        val viewModel = viewModel()
        stars.error = RemoteException.Unreachable(IOException("offline"))
        viewModel.uiState.test {
            viewModel.onAlbumAction(TestData.album("1"), MediaAction.Favorite, MediaActionHost())
            assertThat(expectMostRecentItem().message).isEqualTo(MediaActionFailure.FavoriteFailed)
            viewModel.messageShown()
            assertThat(awaitItem().message).isNull()
        }
    }

    @Test
    fun playAlbumsQueuesTheGenreGridInItsOrder() {
        val viewModel = viewModel()

        viewModel.playAlbums(AlbumOrder.Shuffled, QueuePlacement.End)

        assertThat(play.albumPlays).containsExactly(
            RecordingPlayIntents.AlbumPlay(
                AlbumPlayRequest(AlbumSource.Genre("Jazz"), AlbumOrder.Shuffled, QueuePlacement.End),
                QueueSource.Genre("Jazz"),
            ),
        )
    }

    @Test
    fun genreTintStaysInHeroTintRanges() {
        listOf("Rock", "Jazz", "Electronic", "Hip-Hop", "").forEach { name ->
            val dark = hsl(genreTint(name, isDark = true))
            val light = hsl(genreTint(name, isDark = false))
            assertThat(dark.second).isAtMost(0.551f)
            assertThat(dark.third).isIn(com.google.common.collect.Range.closed(0.179f, 0.301f))
            assertThat(light.third).isIn(com.google.common.collect.Range.closed(0.819f, 0.901f))
        }
        assertThat(genreTint("Rock", true)).isEqualTo(genreTint("rock", true))
    }

    /** (hue, saturation, lightness) of an sRGB color. */
    private fun hsl(color: androidx.compose.ui.graphics.Color): Triple<Float, Float, Float> {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val lightness = (max + min) / 2f
        val delta = max - min
        val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        return Triple(0f, saturation, lightness)
    }
}
