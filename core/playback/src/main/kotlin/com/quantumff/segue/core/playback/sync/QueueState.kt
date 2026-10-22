package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.model.QueueSource

/**
 * The player's queue: song IDs in play order (Segue never reshuffles a queue in place), the index
 * of the current song, the playback position, and the queue source. This is what the snapshot
 * keeps and what gets uploaded, without the source: the server's saved queue has no place for it.
 */
data class QueueState(
    val songIds: List<String>,
    val currentIndex: Int,
    val positionMillis: Long,
    val source: QueueSource = QueueSource.Unknown,
)

/** Player events that save the queue. App backgrounding is separate: it flushes at once. */
enum class QueueEvent {
    TrackTransition,
    QueueEdit,
    Pause,
    Seek,
}
