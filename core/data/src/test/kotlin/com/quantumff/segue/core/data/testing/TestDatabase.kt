package com.quantumff.segue.core.data.testing

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.SegueDatabase
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.network.SubsonicClient
import com.quantumff.segue.core.network.model.NetworkAlbum
import com.quantumff.segue.core.network.model.NetworkArtist
import com.quantumff.segue.core.network.model.NetworkChild
import kotlinx.coroutines.flow.MutableStateFlow

fun inMemoryDatabase(): SegueDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SegueDatabase::class.java)
        .allowMainThreadQueries()
        .build()

class TestTransactionRunner(private val db: SegueDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

internal fun libraryWriter(db: SegueDatabase) =
    LibraryWriter(db.albumDao(), db.songDao(), db.playlistDao(), TestTransactionRunner(db))

internal class FakeClientProvider(
    val client: SubsonicClient = FakeSubsonicClient(),
    activeServerId: String? = SERVER_ID,
) : SubsonicClientProvider {
    override val activeServerId = MutableStateFlow(activeServerId)

    override suspend fun client(serverId: String): SubsonicClient = client
}

internal class FakeClock(var now: Long = 1_000_000L) : Clock {
    override fun nowMillis() = now
}

const val SERVER_ID = "srv"

fun networkSong(id: String, albumId: String, track: Int = 1, title: String = id) =
    NetworkChild(id = id, title = title, albumId = albumId, track = track, discNumber = 1, duration = 200)

fun networkAlbum(id: String, artistId: String = "ar1", songs: Int = 2, name: String = id) = NetworkAlbum(
    id = id,
    name = name,
    artist = "Artist $artistId",
    artistId = artistId,
    songCount = songs,
    created = "2024-01-01T00:00:00Z",
    song = (1..songs).map { networkSong("$id-s$it", albumId = id, track = it) },
)

fun networkArtist(id: String, name: String = id) = NetworkArtist(id = id, name = name, albumCount = 1)
