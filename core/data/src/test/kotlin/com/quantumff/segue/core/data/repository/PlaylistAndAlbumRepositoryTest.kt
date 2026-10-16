package com.quantumff.segue.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.sync.LibrarySynchronizer
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.TestTransactionRunner
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.data.testing.networkSong
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.model.NetworkLyricLine
import com.quantumff.segue.core.network.model.NetworkPlaylist
import com.quantumff.segue.core.network.model.NetworkStructuredLyrics
import com.quantumff.segue.core.testing.repository.FakeServerCapabilitiesRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistAndAlbumRepositoryTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()
    private val clients = FakeClientProvider(server)

    @After
    fun tearDown() = db.close()

    private fun TestScope.playlists(): PlaylistRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val synchronizer = LibrarySynchronizer(
            clients, db.artistDao(), db.albumDao(), db.genreDao(), db.playlistDao(), db.syncMetadataDao(),
            libraryWriter(db), TestTransactionRunner(db), FakeClock(), dispatcher,
        )
        return OfflineFirstPlaylistRepository(clients, db.playlistDao(), libraryWriter(db), synchronizer, dispatcher)
    }

    private fun TestScope.albums(): AlbumRepository = OfflineFirstAlbumRepository(
        clients,
        db.albumDao(),
        db.songDao(),
        db.albumListDao(),
        libraryWriter(db),
        TestTransactionRunner(db),
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `a created playlist is stored with its songs`() = runTest {
        val repository = playlists()

        val id = repository.createPlaylist("Road trip", listOf("a", "b"))

        assertThat(repository.playlist(id).first()!!.name).isEqualTo("Road trip")
        assertThat(repository.playlistSongs(id).first().map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `removing songs sends sorted positions and refetches the playlist`() = runTest {
        server.playlists["p"] = NetworkPlaylist(
            id = "p",
            name = "Mix",
            entry = listOf(networkSong("a", "al"), networkSong("b", "al"), networkSong("a", "al")),
        )
        val repository = playlists()

        repository.removeSongs("p", positions = listOf(2, 0))

        assertThat(server.playlistEdits.single()).isEqualTo("update p name=null add=[] remove=[0, 2]")
        assertThat(repository.playlistSongs("p").first().map { it.id }).containsExactly("b")
    }

    @Test
    fun `a failed edit is reported and Room is untouched`() = runTest {
        server.playlists["p"] = NetworkPlaylist(id = "p", name = "Mix")
        val repository = playlists()
        repository.refreshPlaylist("p")
        server.nextWriteError = com.quantumff.segue.core.network.SubsonicException.NotAuthorized("not yours")

        val error = runCatching { repository.updatePlaylist("p", name = "Renamed") }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteException.ServerError::class.java)
        assertThat(repository.playlist("p").first()!!.name).isEqualTo("Mix")
    }

    @Test
    fun `deleting a playlist removes it locally`() = runTest {
        server.playlists["p"] = NetworkPlaylist(id = "p", name = "Mix")
        val repository = playlists()
        repository.refreshPlaylist("p")

        repository.deletePlaylist("p")

        assertThat(repository.playlists.first()).isEmpty()
    }

    @Test
    fun `album lists keep the server's order`() = runTest {
        server.addAlbum(networkAlbum("b"))
        server.addAlbum(networkAlbum("a"))
        val repository = albums()

        repository.refreshAlbumList(AlbumListType.Recent)

        assertThat(repository.albumList(AlbumListType.Recent).first().map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(server.albumListRequests.single()).isEqualTo(Triple(AlbumListType.Recent, 20, 0))
    }

    @Test
    fun `refreshing an album deleted on the server removes it`() = runTest {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("gone")))
        val repository = albums()

        runCatching { repository.refreshAlbum("gone") }

        assertThat(repository.album("gone").first()).isNull()
        assertThat(repository.albumSongs("gone").first()).isEmpty()
    }

    @Test
    fun `album flows follow the active server`() = runTest {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("al", name = "One")))
        libraryWriter(db).writeAlbums("other", listOf(networkAlbum("al", name = "Two")))
        val repository = albums()

        repository.album("al").test {
            assertThat(awaitItem()!!.name).isEqualTo("One")
            clients.activeServerId.value = "other"
            assertThat(awaitItem()!!.name).isEqualTo("Two")
            clients.activeServerId.value = null
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `lyrics are fetched only when supported, synced first, and cached`() = runTest {
        val capabilities = FakeServerCapabilitiesRepository()
        capabilities.byServer[SERVER_ID] = ServerCapabilities.Unknown.copy(
            openSubsonic = true,
            extensions = persistentMapOf(ServerCapabilities.SONG_LYRICS to persistentListOf(1)),
        )
        server.lyrics["s"] = listOf(
            NetworkStructuredLyrics(lang = "xxx", synced = false, line = listOf(NetworkLyricLine(value = "plain"))),
            NetworkStructuredLyrics(
                lang = "eng",
                synced = true,
                offset = -100,
                line = listOf(NetworkLyricLine(1_000, "timed")),
            ),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SubsonicLyricsRepository(clients, capabilities, dispatcher)

        val lyrics = repository.lyrics("s")
        repository.lyrics("s")

        assertThat(lyrics.map { it.synced }).containsExactly(true, false).inOrder()
        assertThat(lyrics.first().offsetMillis).isEqualTo(-100)
        assertThat(lyrics.first().lines.single().startMillis).isEqualTo(1_000)
        assertThat(lyrics.last().lines.single().startMillis).isNull()
        assertThat(server.lyricsRequests).containsExactly("s")
    }

    @Test
    fun `lyrics are empty without the songLyrics extension`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SubsonicLyricsRepository(clients, FakeServerCapabilitiesRepository(), dispatcher)

        assertThat(repository.lyrics("s")).isEmpty()
        assertThat(server.lyricsRequests).isEmpty()
    }
}
