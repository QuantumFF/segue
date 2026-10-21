package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.data.testing.networkSong
import com.quantumff.segue.core.model.AlbumFeed
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.network.SubsonicException
import com.quantumff.segue.core.network.model.NetworkArtistRef
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackRepositoriesTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()
    private val clients = FakeClientProvider(server)
    private val flushRequests = mutableListOf<String>()

    @After
    fun tearDown() = db.close()

    private fun TestScope.scrobbles() = PendingScrobbleRepository(
        clients,
        db.scrobbleDao(),
        { flushRequests += it },
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `now playing is sent at once and not stored`() = runTest {
        scrobbles().nowPlaying("s1")

        assertThat(server.scrobbles).containsExactly(FakeSubsonicClient.Scrobble(listOf("s1"), emptyList(), false))
        assertThat(db.scrobbleDao().oldest(SERVER_ID, 10)).isEmpty()
    }

    @Test
    fun `a failed now playing is dropped`() = runTest {
        server.nextWriteError = SubsonicException.Network(IOException("offline"))
        scrobbles().nowPlaying("s1")
        assertThat(db.scrobbleDao().oldest(SERVER_ID, 10)).isEmpty()
    }

    @Test
    fun `a submitted play is stored and a flush is requested`() = runTest {
        scrobbles().submit("s1", playedAtMillis = 1_000)

        val pending = db.scrobbleDao().oldest(SERVER_ID, 10).single()
        assertThat(pending.songId).isEqualTo("s1")
        assertThat(pending.playedAt).isEqualTo(1_000)
        assertThat(pending.submission).isTrue()
        assertThat(flushRequests).containsExactly(SERVER_ID)
        assertThat(server.scrobbles).isEmpty()
    }

    @Test
    fun `the flusher sends pending plays in batches, oldest first, and deletes them`() = runTest {
        val repository = scrobbles()
        repeat(60) { repository.submit("s$it", playedAtMillis = it.toLong()) }

        ScrobbleFlusher(clients, db.scrobbleDao()).flush(SERVER_ID)

        assertThat(server.scrobbles.map { it.songIds.size }).containsExactly(50, 10).inOrder()
        assertThat(server.scrobbles.first().timesMillis.take(2)).containsExactly(0L, 1L).inOrder()
        assertThat(server.scrobbles.all { it.submission }).isTrue()
        assertThat(db.scrobbleDao().oldest(SERVER_ID, 100)).isEmpty()
    }

    @Test
    fun `plays that fail to send stay pending`() = runTest {
        scrobbles().submit("s1", 1)
        server.nextWriteError = SubsonicException.Network(IOException("offline"))

        val error = runCatching { ScrobbleFlusher(clients, db.scrobbleDao()).flush(SERVER_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteException.Unreachable::class.java)
        assertThat(db.scrobbleDao().oldest(SERVER_ID, 10)).hasSize(1)
    }

    @Test
    fun `stream locations carry a cache key per server, song and quality`() = runTest {
        val urls = SubsonicMediaUrlRepository(clients)
        val original = urls.stream("s1", StreamingQuality.Original)
        val opus = urls.stream("s1", StreamingQuality(128, "opus"))

        assertThat(original.cacheKey).isEqualTo("$SERVER_ID/s1/0/raw")
        assertThat(opus.cacheKey).isEqualTo("$SERVER_ID/s1/128/opus")
        assertThat(opus.url).contains("s1")
    }

    @Test
    fun `album shuffle reads whole albums of the library and of an artist`() = runTest {
        libraryWriter(db).writeAlbums(
            SERVER_ID,
            listOf(networkAlbum("a1", artistId = "ar1", songs = 3), networkAlbum("a2", artistId = "ar2", songs = 2)),
        )
        val repository = RoomAlbumShuffleRepository(
            clients,
            db.albumDao(),
            db.songDao(),
            db.playlistDao(),
            StandardTestDispatcher(testScheduler),
        )

        assertThat(repository.albumIds(AlbumSource.Library)).containsExactly("a1", "a2")
        assertThat(repository.albumIds(AlbumSource.Artist("ar2"))).containsExactly("a2")
        assertThat(repository.songs(AlbumSource.Library, listOf("a1")).map { it.id })
            .containsExactly("a1-s1", "a1-s2", "a1-s3")
            .inOrder()
    }

    @Test
    fun `a tracks shuffle draws on every song of the source`() = runTest {
        libraryWriter(db).writeAlbums(
            SERVER_ID,
            listOf(networkAlbum("a1", artistId = "ar1", songs = 3), networkAlbum("a2", artistId = "ar2", songs = 2)),
        )
        val repository = RoomAlbumShuffleRepository(
            clients,
            db.albumDao(),
            db.songDao(),
            db.playlistDao(),
            StandardTestDispatcher(testScheduler),
        )

        assertThat(repository.allSongs(AlbumSource.Library).map { it.id })
            .containsExactly("a1-s1", "a1-s2", "a1-s3", "a2-s1", "a2-s2")
        assertThat(repository.allSongs(AlbumSource.Artist("ar2")).map { it.id }).containsExactly("a2-s1", "a2-s2")
    }

    @Test
    fun `an artist's albums come in the artist page's order - their own newest first, then appearances`() = runTest {
        val guest = listOf(NetworkArtistRef("ar1", "Artist ar1"), NetworkArtistRef("ar2", "Artist ar2"))
        libraryWriter(db).writeAlbums(
            SERVER_ID,
            listOf(
                networkAlbum("own-old", artistId = "ar1").copy(year = 2000),
                networkAlbum("guest-new", artistId = "ar2").copy(year = 2020, artists = guest),
                networkAlbum("own-new", artistId = "ar1").copy(year = 2010),
            ),
        )
        val repository = RoomAlbumShuffleRepository(
            clients,
            db.albumDao(),
            db.songDao(),
            db.playlistDao(),
            StandardTestDispatcher(testScheduler),
        )

        assertThat(repository.albumIds(AlbumSource.Artist("ar1")))
            .containsExactly("own-new", "own-old", "guest-new")
            .inOrder()
    }

    @Test
    fun `the Library albums view source lists albums in the order the view shows them`() = runTest {
        libraryWriter(db).writeAlbums(
            SERVER_ID,
            listOf(
                networkAlbum("a1", name = "Zebra", songs = 1),
                networkAlbum("a2", name = "Apple", songs = 2),
                networkAlbum("a3", name = "Mango", songs = 1),
            ),
        )
        val repository = RoomAlbumShuffleRepository(
            clients,
            db.albumDao(),
            db.songDao(),
            db.playlistDao(),
            StandardTestDispatcher(testScheduler),
        )
        val byName = AlbumSource.Albums(AlbumSortOrder.Name)

        assertThat(repository.albumIds(byName)).containsExactly("a2", "a3", "a1").inOrder()
        assertThat(repository.albumIds(AlbumSource.Albums(AlbumSortOrder.Name, AlbumFilter(starred = true))))
            .isEmpty()
        assertThat(repository.songs(byName, listOf("a2")).map { it.id }).containsExactly("a2-s1", "a2-s2").inOrder()
    }

    @Test
    fun `a genre source lists the albums tagged with it by name, as the genre page does`() = runTest {
        libraryWriter(db).writeAlbums(
            SERVER_ID,
            listOf(
                networkAlbum("rock-b", name = "B").copy(genre = "Rock"),
                networkAlbum("rock-a", name = "A").copy(genre = "Rock"),
                // A Jazz album with one Rock song is not one of Rock's albums.
                networkAlbum("jazz", name = "C").copy(
                    genre = "Jazz",
                    song = listOf(networkSong("jazz-s1", albumId = "jazz", track = 1).copy(genre = "Rock")),
                ),
            ),
        )
        val repository = RoomAlbumShuffleRepository(
            clients,
            db.albumDao(),
            db.songDao(),
            db.playlistDao(),
            StandardTestDispatcher(testScheduler),
        )

        assertThat(repository.albumIds(AlbumSource.Genre("Rock"))).containsExactly("rock-a", "rock-b").inOrder()
    }

    @Test
    fun `an album feed round-trips per server and clears`() = runTest {
        val repository = RoomAlbumFeedRepository(clients, db.queueDao())
        val feeds = listOf(
            AlbumFeed(
                source = AlbumSource.Albums(AlbumSortOrder.Year, AlbumFilter(true, "Rock", 1999, true)),
                order = AlbumOrder.InOrder,
                seed = 7,
                cursor = 12,
                queueFingerprint = -3,
            ),
            AlbumFeed(AlbumSource.Library, AlbumOrder.Shuffled, seed = 1, cursor = 0, queueFingerprint = 9),
            AlbumFeed(AlbumSource.Artist("ar1"), AlbumOrder.Shuffled, seed = 2, cursor = 1, queueFingerprint = 9),
            AlbumFeed(AlbumSource.Genre("Jazz"), AlbumOrder.Shuffled, seed = 3, cursor = 2, queueFingerprint = 9),
            AlbumFeed(AlbumSource.Playlist("p1"), AlbumOrder.InOrder, seed = 4, cursor = 3, queueFingerprint = 9),
        )

        assertThat(repository.feed()).isNull()
        for (feed in feeds) {
            repository.save(feed)
            assertThat(repository.feed()).isEqualTo(feed)
        }
        repository.clear()
        assertThat(repository.feed()).isNull()
    }

    @Test
    fun `songs by id keep order and duplicates and skip unknown ids`() = runTest {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("a1", songs = 2)))
        val repository = OfflineFirstLibraryRepository(
            clients,
            db.songDao(),
            db.genreDao(),
            StandardTestDispatcher(testScheduler),
        )

        val songs = repository.songsByIds(listOf("a1-s2", "missing", "a1-s1", "a1-s2"))

        assertThat(songs.map { it.id }).containsExactly("a1-s2", "a1-s1", "a1-s2").inOrder()
    }
}
