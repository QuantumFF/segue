package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.ServerCapabilitiesDao
import com.quantumff.segue.core.database.model.ExtensionMap
import com.quantumff.segue.core.database.model.ServerCapabilitiesEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.ServerCapabilities
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What the active server supports. Detected at sign-in and on app start, then cached in Room. */
interface ServerCapabilitiesRepository {
    /** [ServerCapabilities.Unknown] until detection has run, or when signed out. */
    val capabilities: Flow<ServerCapabilities>

    /** Detects capabilities of the active server again. Throws RemoteException; the cache stays. */
    suspend fun refresh()
}

internal class CachedServerCapabilitiesRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val dao: ServerCapabilitiesDao,
    private val clock: Clock,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ServerCapabilitiesRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val capabilities: Flow<ServerCapabilities> = clients.activeServerId.flatMapLatest { serverId ->
        if (serverId == null) {
            flowOf(ServerCapabilities.Unknown)
        } else {
            dao.observe(serverId).map { it?.asExternalModel() ?: ServerCapabilities.Unknown }
        }
    }

    override suspend fun refresh() {
        val serverId = clients.requireActiveServerId()
        val detected = withContext(ioDispatcher) { remote { clients.client(serverId).detectCapabilities() } }
        store(serverId, detected)
    }

    /** Cached capabilities, detecting them first if this server has none yet. */
    suspend fun forServer(serverId: String): ServerCapabilities = withContext(ioDispatcher) {
        dao.get(serverId)?.asExternalModel()
            ?: remote { clients.client(serverId).detectCapabilities() }.also { store(serverId, it) }
    }

    suspend fun store(serverId: String, capabilities: ServerCapabilities) {
        dao.upsert(
            ServerCapabilitiesEntity(
                serverId = serverId,
                apiVersion = capabilities.apiVersion,
                openSubsonic = capabilities.openSubsonic,
                serverType = capabilities.serverType,
                serverVersion = capabilities.serverVersion,
                extensions = ExtensionMap(capabilities.extensions.mapValues { it.value.toList() }),
                detectedAt = clock.nowMillis(),
            ),
        )
    }
}
