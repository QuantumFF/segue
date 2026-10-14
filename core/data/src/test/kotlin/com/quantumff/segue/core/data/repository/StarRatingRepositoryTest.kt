package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.network.SubsonicException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StarRatingRepositoryTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()
    private val clock = FakeClock(now = 5_000)

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("al")))
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository() = OptimisticStarRatingRepository(
        clients = FakeClientProvider(server),
        songDao = db.songDao(),
        albumDao = db.albumDao(),
        artistDao = db.artistDao(),
        clock = clock,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private suspend fun song() = db.songDao().observe(SERVER_ID, "al-s1").first()!!

    @Test
    fun `starring updates Room and the server`() = runTest {
        repository().setStarred(MediaKind.Song, "al-s1", starred = true)

        assertThat(song().starredAt).isEqualTo(5_000)
        assertThat(server.starCalls).containsExactly("star songs=[al-s1] albums=[] artists=[]")
    }

    @Test
    fun `a failed star is rolled back and reported`() = runTest {
        server.nextWriteError = SubsonicException.Network(java.io.IOException("offline"))

        val error = runCatching { repository().setStarred(MediaKind.Song, "al-s1", starred = true) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteException.Unreachable::class.java)
        assertThat(song().starredAt).isNull()
    }

    @Test
    fun `unstarring an album clears it`() = runTest {
        val repository = repository()
        repository.setStarred(MediaKind.Album, "al", starred = true)
        repository.setStarred(MediaKind.Album, "al", starred = false)

        assertThat(db.albumDao().observe(SERVER_ID, "al").first()!!.starredAt).isNull()
        assertThat(server.starCalls.last()).isEqualTo("unstar songs=[] albums=[al] artists=[]")
    }

    @Test
    fun `rating 0 clears the rating, and a failed rating rolls back`() = runTest {
        val repository = repository()
        repository.setRating(MediaKind.Song, "al-s1", 4)
        assertThat(song().userRating).isEqualTo(4)

        server.nextWriteError = SubsonicException.Generic("nope")
        runCatching { repository.setRating(MediaKind.Song, "al-s1", 0) }
        assertThat(song().userRating).isEqualTo(4)

        repository.setRating(MediaKind.Song, "al-s1", 0)
        assertThat(song().userRating).isNull()
        assertThat(server.starCalls).containsExactly("rate al-s1 4", "rate al-s1 0").inOrder()
    }
}
