package com.quantumff.segue.core.playback.testing

import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.playback.sync.QueueState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayQueueRemoteRepository(private val clientName: String) : PlayQueueRemoteRepository {
    var queue: RemotePlayQueue? = null
    var fetchError: Exception? = null
    var saveError: Exception? = null
    var fetches = 0
    val saves = mutableListOf<QueueState>()

    override suspend fun fetch(): RemotePlayQueue? {
        fetches++
        fetchError?.let { throw it }
        return queue
    }

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        saveError?.let { throw it }
        saves += QueueState(songIds, currentIndex, positionMillis)
    }

    override suspend fun deviceClientName(): String = clientName
}

/** Snapshots saved through [save] get `updatedAt` [now] (0 by default, so any timestamped remote queue is newer). */
class FakeQueueSnapshotRepository : QueueSnapshotRepository {
    override val snapshot = MutableStateFlow<QueueSnapshot?>(null)
    val saves = mutableListOf<QueueState>()
    var now = 0L

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        saves += QueueState(songIds, currentIndex, positionMillis)
        snapshot.value = QueueSnapshot(songIds.toImmutableList(), currentIndex, positionMillis, updatedAt = now)
    }
}
