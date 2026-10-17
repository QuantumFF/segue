package com.quantumff.segue.core.data.download

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineLibraryViewTest {
    private val offlineMode = FakeOfflineModeRepository()
    private val downloads = FakeDownloadRepository()
    private val playlists = FakePlaylistRepository()

    /** Records which source the Artists and Songs tabs were read from. */
    private val sources = mutableListOf<String>()
    private val artists = object : ArtistRepository by FakeArtistRepository().seeded() {
        override fun artists(): Flow<PagingData<Artist>> = record("artists", MutableStateFlow(PagingData.empty()))
        override fun downloadedArtists(): Flow<PagingData<Artist>> =
            record("downloadedArtists", MutableStateFlow(PagingData.empty()))
    }
    private val library = object : LibraryRepository by FakeLibraryRepository().seeded() {
        override fun songs(): Flow<PagingData<Song>> = record("songs", MutableStateFlow(PagingData.empty()))
        override fun downloadedSongs(): Flow<PagingData<Song>> =
            record("downloadedSongs", MutableStateFlow(PagingData.empty()))
    }

    private val view = OfflineLibraryView(offlineMode, downloads, artists, library, playlists)

    private fun <T> record(name: String, flow: Flow<T>): Flow<T> = flow.onStart { sources += name }

    private fun FakeArtistRepository.seeded() = apply {
        artistsState.value = listOf(TestData.artist("a1"), TestData.artist("a2"))
    }

    private fun FakeLibraryRepository.seeded() = apply {
        songsState.value = listOf(TestData.song("s1"), TestData.song("s2"), TestData.song("s3"))
        downloadedSongsState.value = listOf(TestData.song("s1"))
    }

    private fun pin(kind: PinKind, id: String) = PinnedDownload(kind, id, id, null, 1, 1, 0, 0, 1f, 1)

    init {
        playlists.playlistsState.value = listOf(TestData.playlist("p1"), TestData.playlist("p2"))
        downloads.overview.value = DownloadOverview(
            pins = persistentListOf(pin(PinKind.Playlist, "p2"), pin(PinKind.Album, "al1")),
        )
        downloads.downloadedLibrary.value = DownloadedLibrary(songIds = setOf("s1"))
        downloads.songDownloads.value = persistentMapOf(
            "s1" to SongDownload("s1", DownloadState.Completed, 1f),
            "s2" to SongDownload("s2", DownloadState.Downloading, 0.5f),
        )
    }

    @Test
    fun `online everything is listed, counted and available`() = runTest {
        val songs = listOf("s1", "s2", "s3").map { TestData.song(it) }

        assertThat(view.playlists.first().map { it.id }).containsExactly("p1", "p2").inOrder()
        assertThat(view.artistCount.first()).isEqualTo(2)
        assertThat(view.songCount.first()).isEqualTo(3)
        assertThat(view.downloadedWhileOffline.first()).isNull()
        assertThat(view.albumFilter(AlbumFilter(starred = true))).isEqualTo(AlbumFilter(starred = true))
        assertThat(songs.map(view.songAvailability.first()::isAvailable)).containsExactly(true, true, true)
    }

    @Test
    fun `offline narrows to downloaded music and pinned playlists`() = runTest {
        offlineMode.isOffline.value = true
        val songs = listOf("s1", "s2", "s3").map { TestData.song(it) }

        assertThat(view.playlists.first().map { it.id }).containsExactly("p2")
        assertThat(view.artistCount.first()).isNull()
        assertThat(view.songCount.first()).isEqualTo(1)
        assertThat(view.downloadedWhileOffline.first()).isEqualTo(DownloadedLibrary(songIds = setOf("s1")))
        assertThat(view.albumFilter(AlbumFilter(starred = true)))
            .isEqualTo(AlbumFilter(starred = true, downloaded = true))
        // Only a finished download makes a row available; one still downloading does not.
        assertThat(songs.map(view.songAvailability.first()::isAvailable)).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun `the artists and songs tabs switch source with offline mode`() = runTest {
        view.artists.first()
        view.songs.first()
        offlineMode.isOffline.value = true
        view.artists.first()
        view.songs.first()

        assertThat(sources).containsExactly("artists", "songs", "downloadedArtists", "downloadedSongs").inOrder()
    }

    @Test
    fun `the album filter follows offline mode as it changes`() = runTest {
        val filter = MutableStateFlow(AlbumFilter())
        val shown = view.albumFilter(filter)

        assertThat(shown.first()).isEqualTo(AlbumFilter())
        offlineMode.isOffline.value = true
        assertThat(shown.first()).isEqualTo(AlbumFilter(downloaded = true))
    }

    @Test
    fun `pin statuses are split by kind`() = runTest {
        val statuses = view.pinStatuses.first()

        assertThat(statuses[PinKind.Playlist]).containsExactly("p2", DownloadStatus.Downloaded)
        assertThat(statuses[PinKind.Album]).containsExactly("al1", DownloadStatus.Downloaded)
        assertThat(statuses[PinKind.Artist]).isEmpty()
    }
}
