package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The play queue saved on the server, from `getPlayQueue` or `getPlayQueueByIndex`.
 *
 * [changedBy] is the client name of whoever saved it. Segue saves with its stable device client
 * name, so a different value means another device or app saved this queue.
 */
data class RemotePlayQueue(
    val songs: ImmutableList<Song>,
    val currentIndex: Int,
    val positionMillis: Long,
    val changedAt: Long?,
    val changedBy: String?,
)

/**
 * The queue as last saved on this device, so playback can resume offline, exactly as it was.
 * [songIds] is the queue in play order (shuffling happens when songs are queued, never in place)
 * and [currentIndex] points into it. [source] is the queue source, kept on this device only (the
 * server's saved queue has no place for it); [QueueSource.Unknown] for a queue saved before it was kept.
 */
data class QueueSnapshot(
    val songIds: ImmutableList<String>,
    val currentIndex: Int,
    val positionMillis: Long,
    val updatedAt: Long,
    val source: QueueSource = QueueSource.Unknown,
)
