package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.ServerUrl
import com.quantumff.segue.core.data.session.SubsonicClientCreator
import com.quantumff.segue.core.data.sync.SyncScheduler
import com.quantumff.segue.core.database.dao.ServerDataDao
import com.quantumff.segue.core.datastore.AccountsDataSource
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.network.SubsonicServerConfig
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Server accounts: sign in, switch, remove. */
interface ServerRepository {
    val servers: Flow<ImmutableList<Server>>

    val activeServer: Flow<Server?>

    /**
     * Checks the credentials, detects the server's capabilities, stores the account with its
     * password encrypted, makes it active, and schedules a library sync. Signing in again to the
     * same address and user updates that account. Throws [RemoteException] (AuthFailed for a wrong
     * password, Unreachable when offline) or IllegalArgumentException for a malformed address.
     */
    suspend fun signIn(baseUrl: String, username: String, password: String, name: String? = null): Server

    suspend fun switchTo(serverId: String)

    /** Forgets the account and deletes everything cached for it. */
    suspend fun remove(serverId: String)

    /**
     * Call on app start: re-detects the active server's capabilities and schedules an incremental
     * sync. Offline or server errors keep the cached capabilities; a rejected password throws
     * [RemoteException.AuthFailed].
     */
    suspend fun refreshActiveServer()
}

internal class AccountServerRepository @Inject constructor(
    private val accounts: AccountsDataSource,
    private val clientCreator: SubsonicClientCreator,
    private val capabilities: ServerCapabilitiesRepository,
    private val syncScheduler: SyncScheduler,
    private val serverDataDao: ServerDataDao,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ServerRepository {
    override val servers: Flow<ImmutableList<Server>> = accounts.accounts.map { it.toImmutableList() }

    override val activeServer: Flow<Server?> = accounts.activeServer

    override suspend fun signIn(baseUrl: String, username: String, password: String, name: String?): Server =
        withContext(ioDispatcher) {
            val url = ServerUrl.normalize(baseUrl)
            val user = username.trim()
            val detected =
                remote { clientCreator.create(SubsonicServerConfig(url, user, password)).detectCapabilities() }

            val existing = accounts.accounts.first().firstOrNull { it.baseUrl == url && it.username == user }
            val server = Server(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name?.takeIf { it.isNotBlank() } ?: existing?.name ?: URI(url).host,
                baseUrl = url,
                username = user,
            )
            accounts.upsert(server, password)
            capabilities.store(server.id, detected)
            accounts.setActive(server.id)
            syncScheduler.requestSync(server.id, fullRefresh = false)
            server
        }

    override suspend fun switchTo(serverId: String) {
        accounts.setActive(serverId)
        syncScheduler.requestSync(serverId, fullRefresh = false)
    }

    override suspend fun remove(serverId: String) = withContext(ioDispatcher) {
        syncScheduler.cancel(serverId)
        accounts.remove(serverId)
        serverDataDao.deleteServer(serverId)
    }

    override suspend fun refreshActiveServer() {
        val serverId = accounts.activeServerId.first() ?: return
        try {
            capabilities.refresh(serverId)
        } catch (e: RemoteException.AuthFailed) {
            throw e
        } catch (_: RemoteException) {
            // Offline or a server hiccup: keep the cached capabilities.
        }
        syncScheduler.requestSync(serverId, fullRefresh = false)
    }
}
