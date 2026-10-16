package com.quantumff.segue.core.playback.sync

/**
 * The player's queue: song IDs in play order (Segue never reshuffles a queue in place), the index
 * of the current song, and the playback position. This is what the snapshot keeps and what gets
 * uploaded.
 */
data class QueueState(val songIds: List<String>, val currentIndex: Int, val positionMillis: Long)

/** Player events that save the queue. App backgrounding is separate: it flushes at once. */
enum class QueueEvent {
    TrackTransition,
    QueueEdit,
    Pause,
    Seek,
}
