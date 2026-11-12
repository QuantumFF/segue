package com.quantumff.segue.feature.queue

import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.playback.QueueControls

/** A player that only writes down what the queue asked of it. */
class RecordingQueueControls : QueueControls {
    val commands = mutableListOf<String>()

    override fun skipTo(timelineIndex: Int) {
        commands += "skipTo $timelineIndex"
    }

    override fun move(from: Int, to: Int) {
        commands += "move $from $to"
    }

    override fun remove(timelineIndex: Int) {
        commands += "remove $timelineIndex"
    }

    override fun undoRemove() {
        commands += "undoRemove"
    }

    override fun toggleFavorite(songId: String?) {
        commands += "toggleFavorite $songId"
    }

    override suspend fun uploadQueue(): Boolean = true

    override suspend fun fetchRemoteQueue(): RemotePlayQueue? = null

    override suspend fun adoptDownloadedQueue(queue: RemotePlayQueue): Boolean = true

    override fun adoptRemoteQueue() {
        commands += "adoptRemoteQueue"
    }

    override fun dismissRemoteQueue() {
        commands += "dismissRemoteQueue"
    }
}
