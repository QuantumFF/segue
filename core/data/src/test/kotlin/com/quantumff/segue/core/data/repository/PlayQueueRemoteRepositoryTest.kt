package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.networkSong
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.model.NetworkPlayQueue
import com.quantumff.segue.core.network.model.NetworkPlayQueueByIndex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayQueueRemoteRepositoryTest {
    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient()

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository(indexBased: Boolean): PlayQueueRemoteRepository {
        server.capabilities = ServerCapabilities.Unknown.copy(
            openSubsonic = indexBased,
            extensions = if (indexBased) {
                persistentMapOf(
                    ServerCapabilities.INDEX_BASED_QUEUE to persistentListOf(1),
                )
            } else {
                persistentMapOf()
            },
        )
        val clients = FakeClientProvider(server)
        val dispatcher = StandardTestDispatcher(testScheduler)
        return SubsonicPlayQueueRemoteRepository(
            clients = clients,
            capabilities = CachedServerCapabilitiesRepository(
                clients,
                db.serverCapabilitiesDao(),
                FakeClock(),
                dispatcher,
            ),
            deviceClientName = { "Segue-1234abcd" },
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `index-based servers keep duplicate tracks and the exact index`() = runTest {
        server.playQueueByIndex = NetworkPlayQueueByIndex(
            entry = listOf(networkSong("a", "al"), networkSong("b", "al"), networkSong("a", "al")),
            currentIndex = 2,
            position = 1_500,
            changed = "2024-05-01T08:00:00Z",
            changedBy = "Feishin",
        )

        val queue = repository(indexBased = true).fetch()!!

        assertThat(queue.songs.map { it.id }).containsExactly("a", "b", "a").inOrder()
        assertThat(queue.currentIndex).isEqualTo(2)
        assertThat(queue.positionMillis).isEqualTo(1_500)
        assertThat(queue.changedAt).isEqualTo(1_714_550_400_000)
        assertThat(queue.changedBy).isEqualTo("Feishin")
    }

    @Test
    fun `index-based servers save by index with this device's client name`() = runTest {
        repository(indexBased = true).save(listOf("a", "b", "a"), currentIndex = 2, positionMillis = 900)

        assertThat(server.savedQueues.single())
            .isEqualTo(FakeSubsonicClient.SavedQueue(true, listOf("a", "b", "a"), "2", 900, "Segue-1234abcd"))
    }

    @Test
    fun `legacy servers map the current id to its index`() = runTest {
        server.playQueue = NetworkPlayQueue(
            entry = listOf(networkSong("a", "al"), networkSong("b", "al")),
            current = "b",
            position = 300,
            changedBy = "Segue-1234abcd",
        )

        val queue = repository(indexBased = false).fetch()!!

        assertThat(queue.currentIndex).isEqualTo(1)
        assertThat(queue.changedBy).isEqualTo("Segue-1234abcd")
    }

    @Test
    fun `legacy servers save the current song id`() = runTest {
        repository(indexBased = false).save(listOf("a", "b"), currentIndex = 1, positionMillis = 0)

        assertThat(server.savedQueues.single())
            .isEqualTo(FakeSubsonicClient.SavedQueue(false, listOf("a", "b"), "b", 0, "Segue-1234abcd"))
    }

    @Test
    fun `no saved queue reads as null`() = runTest {
        assertThat(repository(indexBased = true).fetch()).isNull()
        assertThat(repository(indexBased = false).fetch()).isNull()
    }

    @Test
    fun `the device client name is exposed for changedBy comparisons`() = runTest {
        assertThat(repository(indexBased = true).deviceClientName()).isEqualTo("Segue-1234abcd")
    }
}
