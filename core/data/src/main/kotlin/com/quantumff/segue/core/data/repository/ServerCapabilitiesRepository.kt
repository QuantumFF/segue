package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.ServerCapabilitiesDao
import com.quantumff.segue.core.database.model.ExtensionMap
import com.quantumff.segue.core.database.model.ServerCapabilitiesEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.ServerCapabilities
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What each server supports. Detected at sign-in and on app start, then cached in Room; repositories
 * ask [forServer] before choosing between API variants.
 */
interface ServerCapabilitiesRepository {
    /** Cached capabilities, detecting them first if this server has none yet. Throws RemoteException. */
    suspend fun forServer(serverId: String): ServerCapabilities

    /** Caches [capabilities] detected elsewhere, e.g. while signing in. */
    suspend fun store(serverId: String, capabilities: ServerCapabilities)

    /** Detects capabilities of [serverId] again. Throws RemoteException; the cache stays. */
    suspend fun refresh(serverId: String)
}

internal class CachedServerCapabilitiesRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val dao: ServerCapabilitiesDao,
    private val clock: Clock,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ServerCapabilitiesRepository {
    override suspend fun forServer(serverId: String): ServerCapabilities = withContext(ioDispatcher) {
        dao.get(serverId)?.asExternalModel() ?: detect(serverId).also { store(serverId, it) }
    }

    override suspend fun refresh(serverId: String) {
        store(serverId, withContext(ioDispatcher) { detect(serverId) })
    }

    override suspend fun store(serverId: String, capabilities: ServerCapabilities) {
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

    private suspend fun detect(serverId: String) = remote { clients.client(serverId).detectCapabilities() }
}
