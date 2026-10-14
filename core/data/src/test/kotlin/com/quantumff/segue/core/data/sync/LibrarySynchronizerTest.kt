package com.quantumff.segue.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.TestTransactionRunner
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.data.testing.networkArtist
import com.quantumff.segue.core.data.testing.networkSong
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.network.model.NetworkGenre
import com.quantumff.segue.core.network.model.NetworkPlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibrarySynchronizerTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()
    private val clock = FakeClock()

    @After
    fun tearDown() = db.close()

    private fun TestScope.synchronizer() = LibrarySynchronizer(
        clients = FakeClientProvider(server),
        artistDao = db.artistDao(),
        albumDao = db.albumDao(),
        genreDao = db.genreDao(),
        playlistDao = db.playlistDao(),
        syncMetadataDao = db.syncMetadataDao(),
        writer = libraryWriter(db),
        transactions = TestTransactionRunner(db),
        clock = clock,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun seedLibrary() {
        server.artists = listOf(networkArtist("ar1", "Radiohead"), networkArtist("ar2", "Björk"))
        server.addAlbum(networkAlbum("al1", artistId = "ar1", name = "In Rainbows"))
        server.addAlbum(networkAlbum("al2", artistId = "ar2", name = "Homogenic", songs = 3))
        server.genres = listOf(NetworkGenre("Rock", songCount = 2, albumCount = 1))
        server.playlists["p1"] = NetworkPlaylist(
            id = "p1",
            name = "Mix",
            changed = "2024-01-01T00:00:00Z",
            entry = listOf(networkSong("al2-s1", "al2"), networkSong("al1-s2", "al1")),
        )
    }

    private suspend fun songIds(albumId: String) = db.songDao().observeByAlbum(SERVER_ID, albumId).first().map { it.id }

    @Test
    fun `the first sync stores the whole library`() = runTest {
        seedLibrary()

        synchronizer().sync(SERVER_ID)

        assertThat(db.artistDao().ids(SERVER_ID)).containsExactly("ar1", "ar2")
        assertThat(db.albumDao().ids(SERVER_ID)).containsExactly("al1", "al2")
        assertThat(songIds("al2")).containsExactly("al2-s1", "al2-s2", "al2-s3").inOrder()
        assertThat(db.genreDao().observeAll(SERVER_ID).first().single().name).isEqualTo("Rock")
        assertThat(
            db.playlistDao().observeSongs(SERVER_ID, "p1").first().map {
                it.id
            },
        ).containsExactly("al2-s1", "al1-s2").inOrder()
        val metadata = db.syncMetadataDao().get(SERVER_ID)!!
        assertThat(metadata.lastFullSyncAt).isEqualTo(clock.now)
        assertThat(metadata.artistsLastModified).isEqualTo(server.artistsLastModified)
        assertThat(
            server.albumListRequests.map {
                it.first
            }.distinct(),
        ).containsExactly(AlbumListType.AlphabeticalByName)
    }

    @Test
    fun `a full sync pages through album lists of 500`() = runTest {
        repeat(501) { server.addAlbum(networkAlbum("al$it", songs = 1)) }

        synchronizer().sync(SERVER_ID)

        assertThat(
            server.albumListRequests.map {
                it.second to it.third
            },
        ).containsExactly(500 to 0, 500 to 500).inOrder()
        assertThat(db.albumDao().count(SERVER_ID)).isEqualTo(501)
    }

    @Test
    fun `album details are fetched with bounded concurrency`() = runTest {
        repeat(20) { server.addAlbum(networkAlbum("al$it", songs = 1)) }
        server.albumFetchDelayMillis = 50

        synchronizer().sync(SERVER_ID)

        assertThat(server.maxConcurrentAlbumFetches).isAtMost(LibrarySynchronizer.ALBUM_FETCH_CONCURRENCY)
        assertThat(server.maxConcurrentAlbumFetches).isGreaterThan(1)
        assertThat(server.albumRequests).hasSize(20)
    }

    @Test
    fun `an incremental sync only fetches albums newer than the known ones`() = runTest {
        seedLibrary()
        val synchronizer = synchronizer()
        synchronizer.sync(SERVER_ID)
        server.albumRequests.clear()
        server.albumListRequests.clear()

        server.addAlbum(networkAlbum("new2"), newest = true)
        server.addAlbum(networkAlbum("new1"), newest = true)
        clock.now += 60_000
        synchronizer.sync(SERVER_ID)

        assertThat(server.albumListRequests.map { it.first }.distinct()).containsExactly(AlbumListType.Newest)
        assertThat(server.albumRequests).containsExactly("new1", "new2")
        assertThat(db.albumDao().ids(SERVER_ID)).containsExactly("al1", "al2", "new1", "new2")
        assertThat(db.syncMetadataDao().get(SERVER_ID)!!.lastSyncAt).isEqualTo(clock.now)
    }

    @Test
    fun `unchanged artists are not rewritten when the server returns an empty index`() = runTest {
        seedLibrary()
        server.honorIfModifiedSince = true
        val synchronizer = synchronizer()
        synchronizer.sync(SERVER_ID)

        clock.now += 60_000
        synchronizer.sync(SERVER_ID)

        assertThat(server.artistRequests).containsExactly(null, server.artistsLastModified).inOrder()
        assertThat(db.artistDao().ids(SERVER_ID)).containsExactly("ar1", "ar2")
    }

    @Test
    fun `changed artists are replaced and removed artists deleted`() = runTest {
        seedLibrary()
        val synchronizer = synchronizer()
        synchronizer.sync(SERVER_ID)

        server.artists = listOf(networkArtist("ar1", "Radiohead"), networkArtist("ar3", "Portishead"))
        server.artistsLastModified += 1
        clock.now += 60_000
        synchronizer.sync(SERVER_ID)

        assertThat(db.artistDao().ids(SERVER_ID)).containsExactly("ar1", "ar3")
    }

    @Test
    fun `a full sync removes albums, songs, and playlists deleted on the server`() = runTest {
        seedLibrary()
        val synchronizer = synchronizer()
        synchronizer.sync(SERVER_ID)

        server.albums.remove("al1")
        server.addAlbum(networkAlbum("al2", artistId = "ar2", name = "Homogenic", songs = 2))
        server.playlists.clear()
        synchronizer.sync(SERVER_ID, fullRefresh = true)

        assertThat(db.albumDao().ids(SERVER_ID)).containsExactly("al2")
        assertThat(songIds("al1")).isEmpty()
        assertThat(songIds("al2")).containsExactly("al2-s1", "al2-s2")
        assertThat(db.playlistDao().observeAll(SERVER_ID).first()).isEmpty()
    }

    @Test
    fun `a stale library gets a full sync`() = runTest {
        seedLibrary()
        val synchronizer = synchronizer()
        synchronizer.sync(SERVER_ID)
        server.albumListRequests.clear()

        clock.now += LibrarySynchronizer.FULL_SYNC_MAX_AGE_MILLIS + 1
        synchronizer.sync(SERVER_ID)

        assertThat(server.albumListRequests.map { it.first }).contains(AlbumListType.AlphabeticalByName)
        assertThat(db.syncMetadataDao().get(SERVER_ID)!!.lastFullSyncAt).isEqualTo(clock.now)
    }

    @Test
    fun `only playlists whose changed time moved are refetched`() = runTest {
        seedLibrary()
        server.playlists["p2"] = NetworkPlaylist(id = "p2", name = "Other", changed = "2024-01-01T00:00:00Z")
        val synchronizer = synchronizer()
        synchronizer.sync(SERVER_ID)
        server.playlistRequests.clear()

        server.playlists["p2"] = server.playlists.getValue("p2").copy(
            changed = "2024-02-01T00:00:00Z",
            entry = listOf(networkSong("al1-s1", "al1")),
        )
        clock.now += 60_000
        synchronizer.sync(SERVER_ID)

        assertThat(server.playlistRequests).containsExactly("p2")
        assertThat(db.playlistDao().observeSongs(SERVER_ID, "p2").first().map { it.id }).containsExactly("al1-s1")
    }

    @Test
    fun `an album deleted between listing and fetching does not fail the sync`() = runTest {
        seedLibrary()
        server.addAlbum(networkAlbum("ghost"))
        server.missingOnFetch += "ghost"

        synchronizer().sync(SERVER_ID)

        assertThat(db.albumDao().ids(SERVER_ID)).containsExactly("al1", "al2")
        assertThat(songIds("al1")).hasSize(2)
    }

    @Test
    fun `progress is reported per phase`() = runTest {
        seedLibrary()
        val reported = mutableListOf<SyncProgress.Running>()

        synchronizer().sync(SERVER_ID) { reported += it }

        assertThat(reported.map { it.phase }.distinct())
            .containsExactly(
                SyncPhase.Artists,
                SyncPhase.Genres,
                SyncPhase.Albums,
                SyncPhase.Songs,
                SyncPhase.Playlists,
            )
            .inOrder()
        assertThat(reported.last { it.phase == SyncPhase.Songs }).isEqualTo(SyncProgress.Running(SyncPhase.Songs, 2, 2))
    }
}
