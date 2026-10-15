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
 *
 * [songIds] is the queue in its own (unshuffled) order and [currentIndex] points into it. With
 * shuffle on, [playOrder] lists the indices of [songIds] in the order they play, so a restore can
 * rebuild the same shuffle (including album blocks) instead of reshuffling.
 */
data class QueueSnapshot(
    val songIds: ImmutableList<String>,
    val currentIndex: Int,
    val positionMillis: Long,
    val updatedAt: Long,
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
    /** Indices of [songIds] in play order; null when shuffle is off (play order = queue order). */
    val playOrder: ImmutableList<Int>? = null,
) {
    /** [songIds] in the order they play: what the server and other clients see. */
    val songIdsInPlayOrder: List<String>
        get() = playOrder?.map { songIds[it] } ?: songIds

    /** The current song's position in [songIdsInPlayOrder]. */
    val currentIndexInPlayOrder: Int
        get() = playOrder?.indexOf(currentIndex) ?: currentIndex
}
