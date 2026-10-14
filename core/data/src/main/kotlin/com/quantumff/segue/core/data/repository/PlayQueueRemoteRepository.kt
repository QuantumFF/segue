package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.datastore.UserPreferencesDataSource
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.network.SubsonicDates
import com.quantumff.segue.core.network.model.NetworkChild
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads and writes the play queue saved on the server. Deliberately thin: when to save and
 * whether to adopt a remote queue is the playback layer's policy.
 *
 * Uses the OpenSubsonic `indexBasedQueue` endpoints when the server has them, because they keep
 * duplicate tracks apart.
 */
interface PlayQueueRemoteRepository {
    /**
     * Null when nothing is saved. The queue's songs are also stored in Room, so a local snapshot of
     * an adopted queue can be restored offline. With the ID-based endpoints, a current song that
     * appears more than once maps to its first occurrence. Throws RemoteException.
     */
    suspend fun fetch(): RemotePlayQueue?

    /** Saves [songIds] in effective play order. Empty [songIds] clears the saved queue. Throws RemoteException. */
    suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long)

    /** The `changedBy` this device saves with. A remote queue with another value came from elsewhere. */
    suspend fun deviceClientName(): String
}

/** This device's client name for queue saves. */
internal fun interface DeviceClientName {
    suspend fun get(): String
}

/** `Segue-` plus the first 8 characters of the stable install ID. */
internal class PreferencesDeviceClientName @Inject constructor(private val preferences: UserPreferencesDataSource) :
    DeviceClientName {
    override suspend fun get(): String = "Segue-" + preferences.deviceId().take(DEVICE_ID_CHARS)

    private companion object {
        const val DEVICE_ID_CHARS = 8
    }
}

internal class SubsonicPlayQueueRemoteRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val capabilities: CachedServerCapabilitiesRepository,
    private val deviceClientName: DeviceClientName,
    private val writer: LibraryWriter,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : PlayQueueRemoteRepository {
    override suspend fun fetch(): RemotePlayQueue? = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val client = clients.client(serverId)
        if (capabilities.forServer(serverId).supportsIndexBasedQueue) {
            val queue = remote { client.getPlayQueueByIndex() } ?: return@withContext null
            remoteQueue(serverId, queue.entry, queue.currentIndex, queue.position, queue.changed, queue.changedBy)
        } else {
            val queue = remote { client.getPlayQueue() } ?: return@withContext null
            val index = queue.entry.indexOfFirst { it.id == queue.current }.coerceAtLeast(0)
            remoteQueue(serverId, queue.entry, index, queue.position, queue.changed, queue.changedBy)
        }
    }

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) =
        withContext(ioDispatcher) {
            val serverId = clients.requireActiveServerId()
            val client = clients.client(serverId)
            val clientName = deviceClientName.get()
            remote {
                if (capabilities.forServer(serverId).supportsIndexBasedQueue) {
                    client.savePlayQueueByIndex(songIds, currentIndex, positionMillis, clientName)
                } else {
                    client.savePlayQueue(songIds, songIds.getOrNull(currentIndex), positionMillis, clientName)
                }
            }
        }

    override suspend fun deviceClientName(): String = deviceClientName.get()

    @Suppress("LongParameterList")
    private suspend fun remoteQueue(
        serverId: String,
        entries: List<NetworkChild>,
        currentIndex: Int,
        positionMillis: Long,
        changed: String?,
        changedBy: String?,
    ): RemotePlayQueue {
        val songEntries = entries.filterNot { it.isDir }
        writer.writeSongs(serverId, songEntries)
        val songs = songEntries.map { it.asEntity(serverId).asExternalModel() }
        return RemotePlayQueue(
            songs = songs.toImmutableList(),
            currentIndex = currentIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
            positionMillis = positionMillis,
            changedAt = SubsonicDates.toEpochMillis(changed),
            changedBy = changedBy?.takeIf { it.isNotBlank() },
        )
    }
}
