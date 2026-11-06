package com.quantumff.segue.core.data.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.SubsonicLyricsRepository
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.TestTransactionRunner
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.datastore.DownloadPreferencesDataSource
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.SubsonicException
import com.quantumff.segue.core.network.model.NetworkLyricLine
import com.quantumff.segue.core.network.model.NetworkStructuredLyrics
import com.quantumff.segue.core.testing.repository.FakeAlbumRepository
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeServerCapabilitiesRepository
import java.io.File
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lyrics kept with downloads (story 30): stored when a download completes, deleted with it. */
@RunWith(RobolectricTestRunner::class)
class DownloadedLyricsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()
    private val clients = FakeClientProvider(server)
    private val capabilities = FakeServerCapabilitiesRepository().apply {
        byServer[SERVER_ID] = ServerCapabilities.Unknown.copy(
            openSubsonic = true,
            extensions = persistentMapOf(ServerCapabilities.SONG_LYRICS to persistentListOf(1)),
        )
    }

    @After
    fun tearDown() = db.close()

    /** A fresh instance has an empty memory cache, like after the app restarts. */
    private fun TestScope.lyrics() = SubsonicLyricsRepository(
        clients = clients,
        capabilities = capabilities,
        lyricsDao = db.lyricsDao(),
        downloads = db.downloadDao(),
        transactions = TestTransactionRunner(db),
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.downloads() = OfflineDownloadRepository(
        clients = clients,
        dao = db.downloadDao(),
        lyrics = db.lyricsDao(),
        files = DownloadFiles(tmp.newFolder("downloads")),
        progress = DownloadProgressTracker(),
        scheduler = { _: DownloadPreferences, _: Boolean -> },
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

    /** Pins [songIds] one by one and completes their downloads with lyrics kept. */
    private suspend fun TestScope.downloadedWithLyrics(downloads: OfflineDownloadRepository, vararg songIds: String) {
        db.songDao().upsertAll(songIds.map { SongEntity(SERVER_ID, it, it, it) })
        songIds.forEach { songId ->
            serverHasLyrics(songId)
            downloads.pin(PinTarget(PinKind.Song, songId, songId, null))
            completed(songId)
            lyrics().keep(SERVER_ID, songId)
        }
    }

    private suspend fun completed(songId: String) =
        db.downloadDao().upsert(DownloadEntity(SERVER_ID, songId, DownloadState.Completed, 5, 5, "/f", updatedAt = 1))

    private fun serverHasLyrics(songId: String, text: String = "la la") {
        server.lyrics[songId] = listOf(
            NetworkStructuredLyrics(lang = "eng", synced = true, line = listOf(NetworkLyricLine(1_000, text))),
        )
    }

    private fun goOffline() {
        server.lyricsError = SubsonicException.Network(IOException("offline"))
    }

    @Test
    fun `a completed download keeps its lyrics, which then load without the network`() = runTest {
        serverHasLyrics("so")
        completed("so")

        lyrics().keep(SERVER_ID, "so")
        goOffline()
        val offline = lyrics().lyrics("so")

        assertThat(offline.single().lines.single().text).isEqualTo("la la")
        assertThat(offline.single().lines.single().startMillis).isEqualTo(1_000)
        assertThat(server.lyricsRequests).containsExactly("so")
    }

    @Test
    fun `a lyrics failure leaves the download complete and stores nothing`() = runTest {
        completed("so")
        goOffline()

        lyrics().keep(SERVER_ID, "so")

        assertThat(db.downloadDao().get(SERVER_ID, "so")?.state).isEqualTo(DownloadState.Completed)
        assertThat(db.lyricsDao().get(SERVER_ID, "so")).isNull()
    }

    @Test
    fun `servers without the songLyrics extension are skipped`() = runTest {
        capabilities.byServer[SERVER_ID] = ServerCapabilities.Unknown
        serverHasLyrics("so")
        completed("so")

        lyrics().keep(SERVER_ID, "so")

        assertThat(server.lyricsRequests).isEmpty()
        assertThat(db.lyricsDao().get(SERVER_ID, "so")).isNull()
    }

    @Test
    fun `a downloaded song the server has no lyrics for loads as none, offline too`() = runTest {
        completed("so")

        lyrics().keep(SERVER_ID, "so")
        goOffline()

        assertThat(lyrics().lyrics("so")).isEmpty()
        assertThat(server.lyricsRequests).containsExactly("so")
    }

    @Test
    fun `a song removed while its lyrics were fetched keeps none`() = runTest {
        serverHasLyrics("so")

        lyrics().keep(SERVER_ID, "so")

        assertThat(db.lyricsDao().get(SERVER_ID, "so")).isNull()
    }

    @Test
    fun `online playback of a song that isn't downloaded stores nothing`() = runTest {
        serverHasLyrics("so")

        assertThat(lyrics().lyrics("so")).hasSize(1)

        assertThat(db.lyricsDao().get(SERVER_ID, "so")).isNull()
    }

    @Test
    fun `unpinning a downloaded song deletes its lyrics and no other song's`() = runTest {
        val downloads = downloads()
        downloadedWithLyrics(downloads, "gone", "kept")

        downloads.unpin(PinKind.Song, "gone")

        assertThat(db.lyricsDao().get(SERVER_ID, "gone")).isNull()
        assertThat(db.lyricsDao().get(SERVER_ID, "kept")).isNotNull()
    }

    @Test
    fun `an undoable unpin keeps the lyrics until the Undo has passed`() = runTest {
        val downloads = downloads()
        downloadedWithLyrics(downloads, "so")

        val removal = checkNotNull(downloads.unpinUndoably(PinKind.Song, "so"))
        downloads.reconcile(SERVER_ID)

        assertThat(db.lyricsDao().get(SERVER_ID, "so")).isNotNull()

        removal.commit().join()

        assertThat(db.lyricsDao().get(SERVER_ID, "so")).isNull()
    }
}
