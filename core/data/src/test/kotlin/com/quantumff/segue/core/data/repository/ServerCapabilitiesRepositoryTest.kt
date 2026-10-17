package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.network.SubsonicException
import java.io.IOException
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
class ServerCapabilitiesRepositoryTest {
    private val db = inMemoryDatabase()
    private val detected = ServerCapabilities.Unknown.copy(
        openSubsonic = true,
        serverType = "navidrome",
        extensions = persistentMapOf(ServerCapabilities.SONG_LYRICS to persistentListOf(1)),
    )
    private val server = FakeSubsonicClient().apply { capabilities = detected }
    private val offline = SubsonicException.Network(IOException("offline"))

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository(): ServerCapabilitiesRepository = CachedServerCapabilitiesRepository(
        FakeClientProvider(server),
        db.serverCapabilitiesDao(),
        FakeClock(),
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `an unknown server is detected once, then answered from the cache`() = runTest {
        val repository = repository()

        assertThat(repository.forServer(SERVER_ID)).isEqualTo(detected)
        server.pingError = offline

        assertThat(repository.forServer(SERVER_ID)).isEqualTo(detected)
    }

    @Test
    fun `stored capabilities are answered without detecting`() = runTest {
        val repository = repository()
        server.pingError = offline

        repository.store(SERVER_ID, detected)

        assertThat(repository.forServer(SERVER_ID)).isEqualTo(detected)
    }

    @Test
    fun `refresh replaces the cache, and a failed refresh keeps it`() = runTest {
        val repository = repository()
        repository.store(SERVER_ID, ServerCapabilities.Unknown)

        repository.refresh(SERVER_ID)
        assertThat(repository.forServer(SERVER_ID)).isEqualTo(detected)

        server.pingError = offline
        val error = runCatching { repository.refresh(SERVER_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteException::class.java)
        assertThat(repository.forServer(SERVER_ID)).isEqualTo(detected)
    }
}
