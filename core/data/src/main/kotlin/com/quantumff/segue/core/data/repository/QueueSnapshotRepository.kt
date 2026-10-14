package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.database.dao.QueueDao
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.model.QueueSnapshot
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The play queue as last saved on this device, per server, for resuming playback offline. */
interface QueueSnapshotRepository {
    val snapshot: Flow<QueueSnapshot?>

    suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long)
}

internal class RoomQueueSnapshotRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val queueDao: QueueDao,
    private val clock: Clock,
) : QueueSnapshotRepository {
    override val snapshot: Flow<QueueSnapshot?> = clients.forActiveServer(null) { serverId ->
        combine(queueDao.observeState(serverId), queueDao.observeSongIds(serverId)) { state, songIds ->
            state?.let {
                QueueSnapshot(songIds.toImmutableList(), it.currentIndex, it.positionMillis, it.updatedAt)
            }
        }
    }

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        val serverId = clients.requireActiveServerId()
        queueDao.replaceSnapshot(QueueStateEntity(serverId, currentIndex, positionMillis, clock.nowMillis()), songIds)
    }
}
