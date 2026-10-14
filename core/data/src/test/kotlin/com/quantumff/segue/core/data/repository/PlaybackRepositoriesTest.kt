package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.network.SubsonicException
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

        assertThat(repository.albumIds(AlbumShuffleSource.Library)).containsExactly("a1", "a2")
        assertThat(repository.albumIds(AlbumShuffleSource.Artist("ar2"))).containsExactly("a2")
        assertThat(repository.songs(AlbumShuffleSource.Library, listOf("a1")).map { it.id })
            .containsExactly("a1-s1", "a1-s2", "a1-s3")
            .inOrder()
    }

    @Test
    fun `songs by id keep order and duplicates and skip unknown ids`() = runTest {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("a1", songs = 2)))
        val repository = OfflineFirstLibraryRepository(
            clients,
            db.songDao(),
            db.genreDao(),
            libraryWriter(db),
            StandardTestDispatcher(testScheduler),
        )

        val songs = repository.songsByIds(listOf("a1-s2", "missing", "a1-s1", "a1-s2"))

        assertThat(songs.map { it.id }).containsExactly("a1-s2", "a1-s1", "a1-s2").inOrder()
    }
}
