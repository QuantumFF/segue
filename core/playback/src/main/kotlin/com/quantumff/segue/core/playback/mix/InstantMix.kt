package com.quantumff.segue.core.playback.mix

import com.quantumff.segue.core.model.Song

/**
 * What starting an Instant mix did: how many songs it [queued], 0 if none, and whether it
 * [replacedQueue], one that Undo ([com.quantumff.segue.core.playback.PlaybackController.undoInstantMix])
 * can bring back.
 */
data class InstantMixStart(val queued: Int, val replacedQueue: Boolean)

/** Builds an instant mix queue from the server's similar songs (`getSimilarSongs2`), topped up with top songs. */
object InstantMix {
    const val DEFAULT_SIZE = 50

    /** [seed] first (if any), then [similar], then [top] until [limit]; each song once. */
    fun build(seed: Song?, similar: List<Song>, top: List<Song>, limit: Int = DEFAULT_SIZE): List<Song> {
        val seen = HashSet<String>()
        return sequenceOf(listOfNotNull(seed), similar, top)
            .flatMap { it.asSequence() }
            .filter { seen.add(it.id) }
            .take(limit)
            .toList()
    }
}
