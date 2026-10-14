package com.quantumff.segue.core.data.repository

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.session.SubsonicClientCreator
import com.quantumff.segue.core.data.sync.SyncScheduler
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.data.testing.libraryWriter
import com.quantumff.segue.core.data.testing.networkAlbum
import com.quantumff.segue.core.datastore.AccountsDataSource
import com.quantumff.segue.core.datastore.StoredAccountsSerializer
import com.quantumff.segue.core.datastore.crypto.AesGcmPasswordCipher
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.network.SubsonicException
import com.quantumff.segue.core.network.SubsonicServerConfig
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val server = FakeSubsonicClient().apply {
        capabilities = ServerCapabilities.Unknown.copy(
            openSubsonic = true,
            serverType = "navidrome",
            extensions = persistentMapOf(ServerCapabilities.SONG_LYRICS to persistentListOf(1, 2)),
        )
    }
    private val createdConfigs = mutableListOf<SubsonicServerConfig>()
    private val scheduler = FakeSyncScheduler()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @After
    fun tearDown() = db.close()

    private class Env(
        val repository: AccountServerRepository,
        val accounts: AccountsDataSource,
        val clients: FakeClientProvider,
    )

    private fun TestScope.env(): Env {
        val dataStore = DataStoreFactory.create(StoredAccountsSerializer, scope = backgroundScope) {
            File(tmp.root, "accounts.json")
        }
        val accounts = AccountsDataSource(dataStore, AesGcmPasswordCipher { key })
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clients = FakeClientProvider(server, activeServerId = null)
        val capabilities =
            CachedServerCapabilitiesRepository(clients, db.serverCapabilitiesDao(), FakeClock(), dispatcher)
        val creator = SubsonicClientCreator { config ->
            createdConfigs += config
            server
        }
        val repository =
            AccountServerRepository(accounts, creator, capabilities, scheduler, db.serverDataDao(), dispatcher)
        return Env(repository, accounts, clients)
    }

    @Test
    fun `signing in stores the account, activates it, caches capabilities, and schedules a sync`() = runTest {
        val env = env()

        val signedIn = env.repository.signIn("music.example.com/", " admin ", "sesame")

        assertThat(signedIn.baseUrl).isEqualTo("https://music.example.com")
        assertThat(signedIn.username).isEqualTo("admin")
        assertThat(signedIn.name).isEqualTo("music.example.com")
        assertThat(
            createdConfigs.single(),
        ).isEqualTo(SubsonicServerConfig("https://music.example.com", "admin", "sesame"))
        assertThat(env.repository.activeServer.first()).isEqualTo(signedIn)
        assertThat(env.accounts.password(signedIn.id)).isEqualTo("sesame")
        assertThat(db.serverCapabilitiesDao().get(signedIn.id)!!.serverType).isEqualTo("navidrome")
        assertThat(scheduler.requests).containsExactly(signedIn.id to false)
    }

    @Test
    fun `a wrong password stores nothing`() = runTest {
        val env = env()
        server.pingError = SubsonicException.WrongCredentials("Wrong username or password")

        val error = runCatching {
            env.repository.signIn("https://music.example.com", "admin", "nope")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteException.AuthFailed::class.java)
        assertThat(env.repository.servers.first()).isEmpty()
        assertThat(scheduler.requests).isEmpty()
    }

    @Test
    fun `signing in again to the same server and user updates that account`() = runTest {
        val env = env()
        val first = env.repository.signIn("https://music.example.com", "admin", "old", name = "Home")

        val second = env.repository.signIn("https://music.example.com/", "admin", "new")

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.name).isEqualTo("Home")
        assertThat(env.repository.servers.first()).hasSize(1)
        assertThat(env.accounts.password(first.id)).isEqualTo("new")
    }

    @Test
    fun `removing a server deletes its cached library and cancels its sync`() = runTest {
        val env = env()
        val signedIn = env.repository.signIn("https://music.example.com", "admin", "sesame")
        libraryWriter(db).writeAlbums(signedIn.id, listOf(networkAlbum("al")))

        env.repository.remove(signedIn.id)

        assertThat(env.repository.servers.first()).isEmpty()
        assertThat(db.albumDao().ids(signedIn.id)).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(signedIn.id)
    }

    @Test
    fun `refreshing offline keeps cached capabilities and still schedules a sync`() = runTest {
        val env = env()
        val signedIn = env.repository.signIn("https://music.example.com", "admin", "sesame")
        env.clients.activeServerId.value = signedIn.id
        server.pingError = SubsonicException.Network(java.io.IOException("offline"))

        env.repository.refreshActiveServer()

        assertThat(db.serverCapabilitiesDao().get(signedIn.id)!!.openSubsonic).isTrue()
        assertThat(scheduler.requests).hasSize(2)
    }

    @Test
    fun `refreshing with a rejected password reports it`() = runTest {
        val env = env()
        val signedIn = env.repository.signIn("https://music.example.com", "admin", "sesame")
        env.clients.activeServerId.value = signedIn.id
        server.pingError = SubsonicException.WrongCredentials("changed")

        val error = runCatching { env.repository.refreshActiveServer() }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteException.AuthFailed::class.java)
    }

    private class FakeSyncScheduler : SyncScheduler {
        val requests = mutableListOf<Pair<String, Boolean>>()
        val cancelled = mutableListOf<String>()

        override fun requestSync(serverId: String, fullRefresh: Boolean) {
            requests += serverId to fullRefresh
        }

        override fun cancel(serverId: String) {
            cancelled += serverId
        }

        override fun progress(serverId: String): Flow<SyncProgress> = flowOf(SyncProgress.Idle)
    }
}
