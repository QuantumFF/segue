package com.quantumff.segue.core.data.session

import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.datastore.AccountsDataSource
import com.quantumff.segue.core.network.SubsonicClient
import com.quantumff.segue.core.network.SubsonicServerConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hands out a [SubsonicClient] for a stored account. */
internal interface SubsonicClientProvider {
    val activeServerId: Flow<String?>

    /** Throws [RemoteException.NotSignedIn] when the account or its password is gone. */
    suspend fun client(serverId: String): SubsonicClient
}

internal suspend fun SubsonicClientProvider.requireActiveServerId(): String =
    activeServerId.first() ?: throw RemoteException.NotSignedIn()

/** Caches one client per account; a changed URL, username, or password makes a new one. */
@Singleton
internal class AccountClientProvider @Inject constructor(
    private val accounts: AccountsDataSource,
    private val clientCreator: SubsonicClientCreator,
) : SubsonicClientProvider {
    private val mutex = Mutex()
    private val clients = mutableMapOf<String, Pair<SubsonicServerConfig, SubsonicClient>>()

    override val activeServerId: Flow<String?> = accounts.activeServerId

    override suspend fun client(serverId: String): SubsonicClient {
        val server = accounts.accounts.first().firstOrNull { it.id == serverId } ?: throw RemoteException.NotSignedIn()
        val password = accounts.password(serverId) ?: throw RemoteException.NotSignedIn()
        val config = SubsonicServerConfig(baseUrl = server.baseUrl, username = server.username, password = password)
        return mutex.withLock {
            clients[serverId]?.takeIf { it.first == config }?.second
                ?: clientCreator.create(config).also { clients[serverId] = config to it }
        }
    }
}
