package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.QueueDao
import com.quantumff.segue.core.database.model.IndexList
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.ShuffleMode
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The play queue as last saved on this device, per server, for resuming playback offline, plus
 * the queue sync bookkeeping that goes with it.
 */
interface QueueSnapshotRepository {
    val snapshot: Flow<QueueSnapshot?>

    /**
     * Saves the queue in queue (unshuffled) order. [currentIndex] points into [songIds];
     * [playOrder] lists indices of [songIds] in play order while [shuffleMode] is not Off.
     * Throws RemoteException when signed out.
     */
    suspend fun save(
        songIds: List<String>,
        currentIndex: Int,
        positionMillis: Long,
        shuffleMode: ShuffleMode = ShuffleMode.Off,
        playOrder: List<Int>? = null,
    )

    /**
     * The server's `changed` time of the newest server queue this device has accounted for (its
     * own save, or another client's that was adopted or dismissed). Null if none. A server
     * timestamp, so it is only ever compared with other server timestamps. Throws RemoteException
     * when signed out.
     */
    suspend fun seenRemoteChange(): Long?

    /** Records a server queue change as accounted for. Never moves the value back. Throws RemoteException when signed out. */
    suspend fun markRemoteChangeSeen(changedAt: Long)
}

internal class RoomQueueSnapshotRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val queueDao: QueueDao,
    private val clock: Clock,
) : QueueSnapshotRepository {
    override val snapshot: Flow<QueueSnapshot?> = clients.forActiveServer(null) { serverId ->
        combine(queueDao.observeState(serverId), queueDao.observeSongIds(serverId)) { state, songIds ->
            state?.let {
                QueueSnapshot(
                    songIds = songIds.toImmutableList(),
                    currentIndex = it.currentIndex,
                    positionMillis = it.positionMillis,
                    updatedAt = it.updatedAt,
                    shuffleMode = it.shuffleMode,
                    // A play order that does not fit the items (should not happen) restores unshuffled.
                    playOrder = it.playOrder?.items
                        ?.takeIf { order ->
                            it.shuffleMode != ShuffleMode.Off &&
                                order.sorted() == songIds.indices.toList()
                        }
                        ?.toImmutableList(),
                )
            }
        }
    }

    override suspend fun save(
        songIds: List<String>,
        currentIndex: Int,
        positionMillis: Long,
        shuffleMode: ShuffleMode,
        playOrder: List<Int>?,
    ) {
        val serverId = clients.requireActiveServerId()
        val order = playOrder?.takeIf { shuffleMode != ShuffleMode.Off }?.let(::IndexList)
        val state = QueueStateEntity(
            serverId = serverId,
            currentIndex = currentIndex,
            positionMillis = positionMillis,
            updatedAt = clock.nowMillis(),
            shuffleMode = if (order == null) ShuffleMode.Off else shuffleMode,
            playOrder = order,
        )
        queueDao.replaceSnapshot(state, songIds)
    }

    override suspend fun seenRemoteChange(): Long? = queueDao.seenRemoteChangedAt(clients.requireActiveServerId())

    override suspend fun markRemoteChangeSeen(changedAt: Long) =
        queueDao.markRemoteChangeSeen(clients.requireActiveServerId(), changedAt)
}
