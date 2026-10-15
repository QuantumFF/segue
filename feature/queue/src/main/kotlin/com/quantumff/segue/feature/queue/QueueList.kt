package com.quantumff.segue.feature.queue

import androidx.compose.runtime.Immutable
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.ShuffleMode
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

    /** Albums shuffle: the header that starts an album block. */
    data class AlbumHeader(
        override val key: String,
        val title: String,
        val artist: String?,
        val coverArtId: String?,
        val trackCount: Int,
    ) : QueueListItem

    data class Track(
        override val key: String,
        /** Position in play order: what move commands take. */
        val playPosition: Int,
        /** Position in the unshuffled queue: what remove and skip take. */
        val timelineIndex: Int,
        val row: TrackRowState,
        /** The album block this row belongs to, in Albums shuffle; null otherwise. */
        val blockKey: String?,
    ) : QueueListItem

    companion object {
        const val HISTORY_KEY = "history"
    }
}

/** A move in play order: [count] tracks from [from] so the first lands at [to]. */
data class QueueMove(val from: Int, val to: Int, val count: Int = 1)

object QueueList {
    /**
     * The rows for [queue] (in play order) with the current track at [currentPosition]: the
     * history row, then the tracks; in [ShuffleMode.Albums], each album block (a run of tracks of
     * one album) starts with a header.
     */
    fun build(
        queue: List<QueueItem>,
        currentPosition: Int,
        mode: ShuffleMode,
        isPlaying: Boolean,
        historyExpanded: Boolean,
    ): ImmutableList<QueueListItem> {
        if (queue.isEmpty()) return emptyList<QueueListItem>().toImmutableList()
        val current = currentPosition.coerceIn(queue.indices)
        val blocks = if (mode == ShuffleMode.Albums) blockKeys(queue) else null
        val keys = trackKeys(queue)
        val rows = ArrayList<QueueListItem>(queue.size + 1)
        if (current > 0) rows += QueueListItem.History(current, historyExpanded)
        val first = if (current > 0 && !historyExpanded) current else 0
        for (position in first until queue.size) {
            val item = queue[position]
            val block = blocks?.get(position)
            val startsBlock = block != null && (position == first || blocks[position - 1] != block)
            if (startsBlock) {
                rows += QueueListItem.AlbumHeader(
                    key = "header:$block",
                    title = item.album.orEmpty(),
                    artist = item.artist,
                    coverArtId = item.coverArtId,
                    trackCount = blocks.count { it == block },
                )
            }
            rows += QueueListItem.Track(
                key = keys[position],
                playPosition = position,
                timelineIndex = item.timelineIndex,
                row = TrackRowState(
                    id = keys[position],
                    title = item.title,
                    artist = item.artist.orEmpty(),
                    // The block header already names the album.
                    album = if (block != null) null else item.album,
                    durationSeconds = item.durationMillis / MILLIS_PER_SECOND,
                    coverArtId = item.coverArtId,
                    isCurrent = position == current,
                    isPlaying = position == current && isPlaying,
                ),
                blockKey = block,
            )
        }
        return rows.toImmutableList()
    }

    /**
     * Where swiping [from] to "play next" moves it: right after the current track, or in Albums
     * shuffle, for a track of another album, right after the current album's block (where "play
     * next" puts new albums too). Null when it is already there.
     */
    fun playNextMove(queue: List<QueueItem>, currentPosition: Int, from: Int, mode: ShuffleMode): QueueMove? {
        if (from !in queue.indices || currentPosition !in queue.indices || from == currentPosition) return null
        var after = currentPosition
        val currentAlbum = queue[currentPosition].albumId
        if (mode == ShuffleMode.Albums && currentAlbum != null && queue[from].albumId != currentAlbum) {
            while (after + 1 < queue.size && queue[after + 1].albumId == currentAlbum) after++
        }
        val to = if (from > after) after + 1 else after
        return if (to == from) null else QueueMove(from, to)
    }

    /**
     * Drag feedback: [items] with the row [fromKey] moved onto [toKey]. A header moves its whole
     * album block, past whole blocks. Rows never move above the history row.
     */
    fun moveLocally(items: List<QueueListItem>, fromKey: Any, toKey: Any): List<QueueListItem> {
        val from = items.indexOfFirst { it.key == fromKey }
        val to = items.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return items
        if (items[from] is QueueListItem.History || items[to] is QueueListItem.History) return items
        val dragged = items[from]
        if (dragged is QueueListItem.Track) {
            return items.toMutableList().apply { add(to, removeAt(from)) }
        }
        val unit = unitRange(items, from)
        val target = unitRange(items, to)
        if (target.first in unit) return items
        val moving = items.subList(unit.first, unit.last + 1)
        val rest = items.filterIndexed { index, _ -> index !in unit }
        val insertAt = if (target.first > unit.first) {
            target.last + 1 - moving.size // After the target unit.
        } else {
            target.first // Before it.
        }
        return rest.toMutableList().apply { addAll(insertAt, moving) }
    }

    /**
     * The play-order move that a finished drag of [draggedKey] made, comparing the rows before the
     * drag ([original]) with the rows now ([local]). Null if nothing moved.
     */
    fun dropMove(original: List<QueueListItem>, local: List<QueueListItem>, draggedKey: Any): QueueMove? {
        val hidden = (original.firstOrNull() as? QueueListItem.History)?.takeUnless { it.expanded }?.count ?: 0
        val start = local.indexOfFirst { it.key == draggedKey }
        if (start < 0) return null
        val moved = when (val dragged = local[start]) {
            is QueueListItem.Track -> listOf(dragged)
            is QueueListItem.AlbumHeader -> local.subList(start, unitRange(local, start).last + 1)
                .filterIsInstance<QueueListItem.Track>()
            is QueueListItem.History -> emptyList()
        }
        if (moved.isEmpty()) return null
        val to = hidden + local.subList(0, start).count { it is QueueListItem.Track }
        val from = moved.first().playPosition
        return if (from == to) null else QueueMove(from, to, moved.size)
    }

    /** The rows that move together with the row at [index]: a header with its block's tracks, or one row. */
    private fun unitRange(items: List<QueueListItem>, index: Int): IntRange {
        val item = items[index]
        val block = when (item) {
            is QueueListItem.AlbumHeader -> item.key.removePrefix("header:")
            is QueueListItem.Track -> item.blockKey
            is QueueListItem.History -> null
        } ?: return index..index
        fun inBlock(i: Int) = items.getOrNull(i).let {
            (it is QueueListItem.Track && it.blockKey == block) || (it is QueueListItem.AlbumHeader && it.key == "header:$block")
        }
        var first = index
        while (inBlock(first - 1)) first--
        var last = index
        while (inBlock(last + 1)) last++
        return first..last
    }

    /** Album block keys per play position: runs of one album; tracks without an album are alone. */
    private fun blockKeys(queue: List<QueueItem>): List<String> {
        val seen = HashMap<String, Int>()
        val keys = ArrayList<String>(queue.size)
        for (position in queue.indices) {
            val album = queue[position].albumId
            val continues = album != null && position > 0 && queue[position - 1].albumId == album
            keys += if (continues) {
                keys.last()
            } else {
                val name = album ?: "single:${queue[position].timelineIndex}"
                val ordinal = seen.merge(name, 1, Int::plus)
                "$name#$ordinal"
            }
        }
        return keys
    }

    /** Song ID plus its occurrence in queue order: stable when the play order changes, unique with duplicates. */
    private fun trackKeys(queue: List<QueueItem>): List<String> {
        val occurrence = HashMap<String, Int>()
        val byTimeline = queue.sortedBy { it.timelineIndex }.associate { item ->
            item.timelineIndex to "${item.songId}#${occurrence.merge(item.songId, 1, Int::plus)}"
        }
        return queue.map { byTimeline.getValue(it.timelineIndex) }
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
