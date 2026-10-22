package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.model.QueueSnapshot

/** Turns the local snapshot back into a player queue. */
object QueueRestore {
    /**
     * The queue to restore from [snapshot], with its source, keeping only songs in [available]
     * (songs no longer in the library are dropped). If the current song is gone, the next available
     * song becomes current (or the previous one at the end), from its start. Null when nothing is
     * left.
     */
    fun plan(snapshot: QueueSnapshot, available: Set<String>): QueueState? {
        val kept = snapshot.songIds.indices.filter { snapshot.songIds[it] in available }
        if (kept.isEmpty()) return null
        val newIndexOf = HashMap<Int, Int>(kept.size).apply { kept.forEachIndexed { new, old -> put(old, new) } }

        val current = newIndexOf[snapshot.currentIndex]
        val (currentIndex, position) = if (current != null) {
            current to snapshot.positionMillis
        } else {
            val next = kept.firstOrNull { it > snapshot.currentIndex }
            val previous = kept.lastOrNull { it < snapshot.currentIndex }
            newIndexOf.getValue(next ?: previous ?: kept.first()) to 0L
        }
        return QueueState(kept.map { snapshot.songIds[it] }, currentIndex, position, snapshot.source)
    }
}
