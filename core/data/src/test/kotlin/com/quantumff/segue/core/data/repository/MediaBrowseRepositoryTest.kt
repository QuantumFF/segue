package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.TestTransactionRunner
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.model.AlbumListType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaBrowseRepositoryTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()
    private val clients = FakeClientProvider(server)

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository(): MediaBrowseRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val albums = OfflineFirstAlbumRepository(
            clients,
            db.albumDao(),
            db.songDao(),
            db.albumListDao(),
            libraryWriter(db),
            TestTransactionRunner(db),
            dispatcher,
        )
        return RoomMediaBrowseRepository(
            clients, db.browseDao(), db.albumDao(), db.albumListDao(), db.artistDao(), db.songDao(), db.genreDao(),
            db.playlistDao(), albums, dispatcher,
        )
    }

    private fun album(id: String, createdAt: Long) =
        AlbumEntity(serverId = SERVER_ID, id = id, name = id, sortName = id, createdAt = createdAt)

    @Test
    fun `recently added falls back to the added date until the server list is refreshed`() = runTest {
        db.albumDao().upsertAll(listOf(album("old", createdAt = 1), album("new", createdAt = 2)))
        val repository = repository()

        assertThat(repository.recentlyAddedAlbums(0, 10).map { it.id }).containsExactly("new", "old").inOrder()

        server.addAlbum(networkAlbum("old"))
        repository.refreshHomeLists()

        assertThat(repository.recentlyAddedAlbums(0, 10).map { it.id }).containsExactly("old")
        assertThat(server.albumListRequests.map { it.first })
            .containsExactly(AlbumListType.Recent, AlbumListType.Newest).inOrder()
    }

    @Test
    fun `server lists are paged in memory`() = runTest {
        listOf("c", "b", "a").forEach { server.addAlbum(networkAlbum(it)) }
        val repository = repository()
        repository.refreshHomeLists()

        assertThat(repository.recentlyPlayedAlbums(offset = 1, limit = 1).map { it.id }).containsExactly("b")
    }

    @Test
    fun `everything is empty when signed out`() = runTest {
        db.albumDao().upsertAll(listOf(album("a", createdAt = 1)))
        clients.activeServerId.value = null
        val repository = repository()

        assertThat(repository.albums(0, 10)).isEmpty()
        assertThat(repository.album("a")).isNull()
    }
}
