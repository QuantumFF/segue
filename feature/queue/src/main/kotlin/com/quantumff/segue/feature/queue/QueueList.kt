package com.quantumff.segue.feature.queue

import androidx.compose.runtime.Immutable
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.model.QueueItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One row of the queue list (DESIGN.md §4.8). [key] is stable for the row's lifetime in a queue state. */
@Immutable
sealed interface QueueListItem {
    val key: String

    /** "N played": the tracks before the current one, collapsed unless [expanded]. */
    data class History(val count: Int, val expanded: Boolean) : QueueListItem {
        override val key: String get() = HISTORY_KEY
    }

    data class Track(
        override val key: String,
        /** Position in the queue, which is also the play order: what move, remove and skip take. */
        val position: Int,
        val row: TrackRowState,
    ) : QueueListItem

    companion object {
        const val HISTORY_KEY = "history"
    }
}

/** A move in the queue: the track at [from] lands at [to]. */
data class QueueMove(val from: Int, val to: Int)

object QueueList {
    /**
     * The rows for [queue] (in play order) with the current track at [currentPosition]: the
     * history row, then the tracks.
     */
    fun build(
        queue: List<QueueItem>,
        currentPosition: Int,
        isPlaying: Boolean,
        historyExpanded: Boolean,
    ): ImmutableList<QueueListItem> {
        if (queue.isEmpty()) return emptyList<QueueListItem>().toImmutableList()
        val current = currentPosition.coerceIn(queue.indices)
        val keys = trackKeys(queue)
        val rows = ArrayList<QueueListItem>(queue.size + 1)
        if (current > 0) rows += QueueListItem.History(current, historyExpanded)
        val first = if (current > 0 && !historyExpanded) current else 0
        for (position in first until queue.size) {
            val item = queue[position]
            rows += QueueListItem.Track(
                key = keys[position],
                position = position,
                row = TrackRowState(
                    id = keys[position],
                    title = item.title,
                    artist = item.artist.orEmpty(),
                    album = item.album,
                    durationSeconds = item.durationMillis / MILLIS_PER_SECOND,
                    coverArtId = item.coverArtId,
                    isCurrent = position == current,
                    isPlaying = position == current && isPlaying,
                ),
            )
        }
        return rows.toImmutableList()
    }

    /** Where swiping [from] to "play next" moves it: right after the current track. Null when it is already there. */
    fun playNextMove(queueSize: Int, currentPosition: Int, from: Int): QueueMove? {
        if (from !in 0 until queueSize || currentPosition !in 0 until queueSize || from == currentPosition) return null
        val to = if (from > currentPosition) currentPosition + 1 else currentPosition
        return if (to == from) null else QueueMove(from, to)
    }

    /** Drag feedback: [items] with the track [fromKey] moved onto [toKey]. Rows never move above the history row. */
    fun moveLocally(items: List<QueueListItem>, fromKey: Any, toKey: Any): List<QueueListItem> {
        val from = items.indexOfFirst { it.key == fromKey }
        val to = items.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return items
        if (items[from] !is QueueListItem.Track || items[to] !is QueueListItem.Track) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * The queue move that a finished drag of [draggedKey] made, comparing the rows before the drag
     * ([original]) with the rows now ([local]). Null if nothing moved.
     */
    fun dropMove(original: List<QueueListItem>, local: List<QueueListItem>, draggedKey: Any): QueueMove? {
        val hidden = (original.firstOrNull() as? QueueListItem.History)?.takeUnless { it.expanded }?.count ?: 0
        val index = local.indexOfFirst { it.key == draggedKey }
        val dragged = local.getOrNull(index) as? QueueListItem.Track ?: return null
        val to = hidden + local.subList(0, index).count { it is QueueListItem.Track }
        return if (dragged.position == to) null else QueueMove(dragged.position, to)
    }

    /** Song ID plus its occurrence in the queue: unique with duplicates. */
    private fun trackKeys(queue: List<QueueItem>): List<String> {
        val occurrence = HashMap<String, Int>()
        return queue.map { "${it.songId}#${occurrence.merge(it.songId, 1, Int::plus)}" }
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
