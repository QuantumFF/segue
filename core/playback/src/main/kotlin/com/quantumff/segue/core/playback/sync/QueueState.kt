package com.quantumff.segue.core.playback.sync

/**
 * The queue as other clients should see it: song IDs in effective play order (shuffled when
 * shuffle is on), the index of the current song in that order, and the playback position.
 */
data class QueueState(val songIds: List<String>, val currentIndex: Int, val positionMillis: Long)

/** Player events that save the queue. App backgrounding is separate: it flushes at once. */
enum class QueueEvent {
    TrackTransition,
    QueueEdit,
    Pause,
    Seek,
}
