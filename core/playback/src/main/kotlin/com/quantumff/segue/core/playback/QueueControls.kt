package com.quantumff.segue.core.playback

import com.quantumff.segue.core.model.RemotePlayQueue

/**
 * What the queue (DESIGN.md §4.8) does to the player: the part of [PlaybackController] the queue
 * screen uses, so it can be tested without a playback service. Positions are queue positions
 * ([com.quantumff.segue.core.model.QueueItem.timelineIndex]).
 */
interface QueueControls {
    fun skipTo(timelineIndex: Int)

    fun move(from: Int, to: Int)

    /** Removes the item at [timelineIndex]. [undoRemove] puts the last removed item back. */
    fun remove(timelineIndex: Int)

    fun undoRemove()

    /** Stars or unstars the queued song [songId], or the current song when null. */
    fun toggleFavorite(songId: String? = null)

    /** Saves the queue to the server now. False if it was not saved. */
    suspend fun uploadQueue(): Boolean

    /** The server's saved queue, or null if there is none. */
    suspend fun fetchRemoteQueue(): RemotePlayQueue?

    /** Replaces the queue with [queue] from [fetchRemoteQueue], paused. False if it did not. */
    suspend fun adoptDownloadedQueue(queue: RemotePlayQueue): Boolean

    /** "Continue from <device>". */
    fun adoptRemoteQueue()

    fun dismissRemoteQueue()
}
