package com.quantumff.segue.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.data.testing.networkSong
import com.quantumff.segue.core.network.SubsonicException
import com.quantumff.segue.core.network.model.NetworkSearchResult3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchRepositoryTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository() = OfflineFirstSearchRepository(
        clients = FakeClientProvider(server),
        searchDao = db.searchDao(),
        writer = libraryWriter(db),
        artistDao = db.artistDao(),
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `local matches come first, then server results are merged in and stored`() = runTest {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("local", name = "Rainbow Bridge")))
        server.searchResult = NetworkSearchResult3(
            album = listOf(networkAlbum("remote", name = "In Rainbows").copy(song = emptyList())),
            song = listOf(networkSong("remote-song", albumId = "remote", title = "Rainbow")),
        )

        repository().search("rainb").test {
            val local = awaitItem()
            assertThat(local.albums.map { it.id }).containsExactly("local")
            assertThat(local.includesServerResults).isFalse()

            val merged = awaitItem()
            assertThat(merged.albums.map { it.id }).containsExactly("remote", "local").inOrder()
            assertThat(merged.songs.map { it.id }).containsExactly("remote-song")
            assertThat(merged.includesServerResults).isTrue()
            awaitComplete()
        }
        assertThat(db.albumDao().observe(SERVER_ID, "remote").first()).isNotNull()
        assertThat(server.searchQueries).containsExactly("rainb")
    }

    @Test
    fun `a failed server search leaves the local results`() = runTest {
        libraryWriter(db).writeAlbums(SERVER_ID, listOf(networkAlbum("local", name = "Rainbow Bridge")))
        server.searchError = SubsonicException.Network(java.io.IOException("offline"))

        repository().search("rainbow").test {
            assertThat(awaitItem().albums).hasSize(1)
            awaitComplete()
        }
    }

    @Test
    fun `a blank query is empty and skips the server`() = runTest {
        repository().search("  ").test {
            assertThat(awaitItem().songs).isEmpty()
            awaitComplete()
        }
        assertThat(server.searchQueries).isEmpty()
    }
}
