package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.model.ShuffleMode

/**
 * The player's queue: song IDs in queue (unshuffled) order, the index of the current song in it,
 * the playback position, and the shuffle. [playOrder] lists indices of [songIds] in the order they
 * play while [shuffleMode] is not Off, so the exact shuffle (album blocks included) can be
 * restored. With shuffle off, [playOrder] is null.
 */
data class QueueState(
    val songIds: List<String>,
    val currentIndex: Int,
    val positionMillis: Long,
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
    val playOrder: List<Int>? = null,
) {
    /**
     * The queue as other clients should see it: songs in effective play order, the current
     * song's index in that order, and no shuffle. This is what gets uploaded.
     */
    fun inPlayOrder(): QueueState {
        val order =
            playOrder ?: return if (shuffleMode == ShuffleMode.Off) this else copy(shuffleMode = ShuffleMode.Off)
        return QueueState(order.map { songIds[it] }, order.indexOf(currentIndex), positionMillis)
    }
}

/** Player events that save the queue. App backgrounding is separate: it flushes at once. */
enum class QueueEvent {
    TrackTransition,
    QueueEdit,
    Pause,
    Seek,
}
