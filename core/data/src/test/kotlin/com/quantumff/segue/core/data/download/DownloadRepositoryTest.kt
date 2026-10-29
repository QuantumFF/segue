package com.quantumff.segue.core.data.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.StorageLimit
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pin and unpin end to end through Room: rows, files, and the reference counting between pins. */
@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val dao = db.downloadDao()
    private val schedules = mutableListOf<Boolean>()

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository(files: DownloadFiles = DownloadFiles(tmp.newFolder("downloads"))) =
        OfflineDownloadRepository(
            clients = FakeClientProvider(),
            dao = dao,
            files = files,
            progress = DownloadProgressTracker(),
            scheduler = { _: DownloadPreferences, restart: Boolean -> schedules += restart },
            preferenceSource = DownloadPreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") },
            ),
            albums = FakeAlbumRepository(),
            artists = FakeArtistRepository(),
            playlists = FakePlaylistRepository(),
            clock = FakeClock(),
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    private suspend fun library() {
        db.albumDao().upsertAll(listOf(AlbumEntity(SERVER_ID, "al", "Album", "album")))
        db.songDao().upsertAll(
            listOf("shared", "only-album").map { SongEntity(SERVER_ID, it, it, it, albumId = "al") } +
                SongEntity(SERVER_ID, "only-playlist", "x", "x"),
        )
        db.playlistDao().replacePlaylist(PlaylistEntity(SERVER_ID, "p", "Mix"), listOf("shared", "only-playlist"))
    }

    private suspend fun completeWithFile(files: DownloadFiles, songId: String): File {
        val file = files.fileFor(SERVER_ID, songId, "mp3").apply {
            parentFile?.mkdirs()
            writeText("audio")
        }
        dao.upsert(
            DownloadEntity(
                SERVER_ID,
                songId,
                DownloadState.Completed,
                file.length(),
                file.length(),
                file.path,
                updatedAt = 1,
            ),
        )
        return file
    }

    @Test
    fun `pinning queues every covered song and schedules the worker`() = runTest {
        library()
        val repository = repository()

        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))

        assertThat(dao.getAll(SERVER_ID).map { it.songId }).containsExactly("shared", "only-album")
        assertThat(dao.getAll(SERVER_ID).map { it.state }.distinct()).containsExactly(DownloadState.Queued)
        assertThat(schedules).containsExactly(false)
    }

    @Test
    fun `the storage limit is saved, reschedules the worker and shows in the overview once reached`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        schedules.clear()

        repository.setStorageLimit(StorageLimit.Gb2)

        assertThat(repository.preferences.first().storageLimit).isEqualTo(StorageLimit.Gb2)
        assertThat(schedules).containsExactly(false)
        assertThat(repository.overview.first().limitReached).isFalse()

        dao.upsert(
            DownloadEntity(SERVER_ID, "shared", DownloadState.Completed, 2_000_000_000L, null, "x", updatedAt = 1),
        )

        val overview = repository.overview.first()
        assertThat(overview.storageLimit).isEqualTo(StorageLimit.Gb2)
        assertThat(overview.limitReached).isTrue()
    }

    @Test
    fun `pause all holds every unfinished download`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Playlist, "p", "Mix", null))
        completeWithFile(files, "shared")

        repository.pauseAll()

        assertThat(dao.get(SERVER_ID, "shared")?.state).isEqualTo(DownloadState.Completed)
        assertThat(dao.get(SERVER_ID, "only-playlist")?.state).isEqualTo(DownloadState.Paused)
    }

    @Test
    fun `cancel all unpins unfinished pins and keeps finished ones`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        repository.pin(PinTarget(PinKind.Song, "shared", "shared", null))
        completeWithFile(files, "shared")

        repository.cancelAll()

        val pins = repository.overview.first().pins
        assertThat(pins.map { it.id }).containsExactly("shared")
        assertThat(dao.getAll(SERVER_ID).map { it.songId }).containsExactly("shared")
    }

    @Test
    fun `unpinning keeps songs another pin still references and deletes the rest`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        repository.pin(PinTarget(PinKind.Playlist, "p", "Mix", null))
        val shared = completeWithFile(files, "shared")
        val onlyAlbum = completeWithFile(files, "only-album")

        repository.unpin(PinKind.Album, "al")

        assertThat(dao.getAll(SERVER_ID).map { it.songId }).containsExactly("shared", "only-playlist")
        assertThat(shared.exists()).isTrue()
        assertThat(onlyAlbum.exists()).isFalse()
        assertThat(repository.localFile("shared")).isEqualTo(shared)

        repository.unpin(PinKind.Playlist, "p")

        assertThat(dao.getAll(SERVER_ID)).isEmpty()
        assertThat(shared.exists()).isFalse()
        assertThat(repository.localFile("shared")).isNull()
    }

    @Test
    fun `pin status reflects its songs and pause holds the unfinished ones`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        completeWithFile(files, "shared")

        repository.pause(PinKind.Album, "al")

        assertThat(dao.get(SERVER_ID, "only-album")?.state).isEqualTo(DownloadState.Paused)
        assertThat(repository.pinStatus(PinKind.Album, "al").first()).isEqualTo(DownloadStatus.Paused)
        assertThat(repository.pinStatus(PinKind.Playlist, "p").first()).isNull()

        repository.resume(PinKind.Album, "al")

        assertThat(dao.get(SERVER_ID, "only-album")?.state).isEqualTo(DownloadState.Queued)
        assertThat(repository.downloadedSongIds.first()).containsExactly("shared")
    }

    @Test
    fun `remove all unpins everything and deletes every file`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Playlist, "p", "Mix", null))
        val file = completeWithFile(files, "shared")

        repository.removeAll()

        assertThat(dao.getAll(SERVER_ID)).isEmpty()
        assertThat(file.exists()).isFalse()
        assertThat(repository.overview.first().pins).isEmpty()
    }

    @Test
    fun `an undoable removal unpins at once and keeps the files, even through cleanup, until committed`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        val file = completeWithFile(files, "only-album")

        val removal = checkNotNull(repository.unpinUndoably(PinKind.Album, "al"))
        // The automatic cleanup that follows pin changes.
        repository.reconcile(SERVER_ID)

        assertThat(repository.pinStatus(PinKind.Album, "al").first()).isNull()
        assertThat(file.exists()).isTrue()
        assertThat(dao.get(SERVER_ID, "only-album")?.state).isEqualTo(DownloadState.Completed)

        removal.commit().join()

        assertThat(dao.getAll(SERVER_ID)).isEmpty()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `undoing a removal pins it again and keeps its files`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        val shared = completeWithFile(files, "shared")
        val onlyAlbum = completeWithFile(files, "only-album")
        val removal = checkNotNull(repository.unpinUndoably(PinKind.Album, "al"))
        repository.reconcile(SERVER_ID)
        schedules.clear()

        removal.undo().join()
        removal.commit().join()

        assertThat(repository.pinStatus(PinKind.Album, "al").first()).isEqualTo(DownloadStatus.Downloaded)
        assertThat(shared.exists()).isTrue()
        assertThat(onlyAlbum.exists()).isTrue()
        assertThat(schedules).isEmpty()
    }

    @Test
    fun `a pending removal holds its unfinished songs and undo queues them again`() = runTest {
        library()
        val files = DownloadFiles(tmp.newFolder("downloads"))
        val repository = repository(files)
        repository.pin(PinTarget(PinKind.Album, "al", "Album", null))
        repository.pin(PinTarget(PinKind.Playlist, "p", "Mix", null))
        completeWithFile(files, "shared")
        schedules.clear()

        val removal = checkNotNull(repository.unpinUndoably(PinKind.Album, "al"))

        assertThat(dao.get(SERVER_ID, "only-album")?.state).isEqualTo(DownloadState.Paused)
        // Another pin still wants this one.
        assertThat(dao.get(SERVER_ID, "only-playlist")?.state).isEqualTo(DownloadState.Queued)

        removal.undo().join()

        assertThat(dao.get(SERVER_ID, "only-album")?.state).isEqualTo(DownloadState.Queued)
        assertThat(schedules).containsExactly(false)
    }
}
