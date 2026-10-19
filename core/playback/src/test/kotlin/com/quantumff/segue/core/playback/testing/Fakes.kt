package com.quantumff.segue.core.playback.testing

import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.playback.sync.QueueState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayQueueRemoteRepository(private val clientName: String) : PlayQueueRemoteRepository {
    var queue: RemotePlayQueue? = null
    var fetchError: Exception? = null
    var saveError: Exception? = null
    var fetchDelayMillis = 0L
    var fetches = 0
    val saves = mutableListOf<QueueState>()

    override suspend fun fetch(): RemotePlayQueue? {
        fetches++
        delay(fetchDelayMillis)
        fetchError?.let { throw it }
        return queue
    }

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        saveError?.let { throw it }
        saves += QueueState(songIds, currentIndex, positionMillis)
    }

    override suspend fun deviceClientName(): String = clientName
}

/** Snapshots saved through [save] get `updatedAt` [now], the device clock, which sync must not rely on. */
class FakeQueueSnapshotRepository : QueueSnapshotRepository {
    override val snapshot = MutableStateFlow<QueueSnapshot?>(null)
    val saves = mutableListOf<QueueState>()
    var now = 0L
    var seen: Long? = null

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) {
        saves += QueueState(songIds, currentIndex, positionMillis)
        snapshot.value = QueueSnapshot(songIds.toImmutableList(), currentIndex, positionMillis, updatedAt = now)
    }

    override suspend fun seenRemoteChange(): Long? = seen

    override suspend fun markRemoteChangeSeen(changedAt: Long) {
        seen = maxOf(seen ?: changedAt, changedAt)
    }
}
