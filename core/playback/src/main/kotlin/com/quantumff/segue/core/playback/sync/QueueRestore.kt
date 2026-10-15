package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.ShuffleMode

/** Turns the local snapshot back into a player queue, shuffle and all. */
object QueueRestore {
    /**
     * The queue to restore from [snapshot], keeping only songs in [available] (songs no longer in
     * the library are dropped). The shuffle play order is renumbered to match. If the current song
     * is gone, the next available song in play order becomes current (or the previous one at the
     * end), from its start. Null when nothing is left.
     */
    fun plan(snapshot: QueueSnapshot, available: Set<String>): QueueState? {
        val kept = snapshot.songIds.indices.filter { snapshot.songIds[it] in available }
        if (kept.isEmpty()) return null
        val newIndexOf = HashMap<Int, Int>(kept.size).apply { kept.forEachIndexed { new, old -> put(old, new) } }
        val oldPlayOrder = snapshot.playOrder ?: snapshot.songIds.indices.toList()
        val playOrder = oldPlayOrder.mapNotNull { newIndexOf[it] }

        val current = newIndexOf[snapshot.currentIndex]
        val (currentIndex, position) = if (current != null) {
            current to snapshot.positionMillis
        } else {
            val missingAt = oldPlayOrder.indexOf(snapshot.currentIndex).coerceAtLeast(0)
            val next = oldPlayOrder.drop(missingAt + 1).firstNotNullOfOrNull { newIndexOf[it] }
            val previous = oldPlayOrder.take(missingAt).lastOrNull { it in newIndexOf }?.let { newIndexOf[it] }
            (next ?: previous ?: playOrder.first()) to 0L
        }
        val shuffled = snapshot.shuffleMode != ShuffleMode.Off && snapshot.playOrder != null
        return QueueState(
            songIds = kept.map { snapshot.songIds[it] },
            currentIndex = currentIndex,
            positionMillis = position,
            shuffleMode = if (shuffled) snapshot.shuffleMode else ShuffleMode.Off,
            playOrder = if (shuffled) playOrder else null,
        )
    }
}
