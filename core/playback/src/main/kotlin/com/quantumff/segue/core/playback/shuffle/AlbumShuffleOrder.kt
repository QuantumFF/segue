package com.quantumff.segue.core.playback.shuffle

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Shuffles whole albums: the queue is grouped into blocks by album ID, the blocks are shuffled with
 * Fisher-Yates, and tracks inside a block keep their queue (disc/track) order. The block holding
 * the current track comes first. See docs/PLAN.md, "Album shuffle".
 *
 * The queue itself is never reordered; this only defines the play order, so turning shuffle off
 * returns to the original order.
 *
 * Rules:
 * - **Split albums.** Tracks of the same album that are not next to each other in the queue still
 *   form one block, joined in the order they first appear (queue `a a b a` gives the block
 *   `a1 a2 a3`). Shuffling by album is about not interleaving albums, so splitting one would defeat it.
 * - **No album.** Tracks with a null album key each form their own block.
 * - **Inserts.** Inserted tracks of the same album as their queue neighbour join that neighbour's
 *   block, right after the previous track or right before the next one. Other inserted tracks form
 *   new blocks (grouped by the rules above): appended to the queue, they play last; inserted
 *   elsewhere ("play next"), they play right after the current album's block. An inserted album
 *   that is already queued somewhere not adjacent becomes a second block: the user chose where
 *   it goes, so it is not pulled into the existing block. The next fresh shuffle merges them.
 * - **Moves** in the queue keep the moment each track plays.
 *
 * [ShuffleOrder] callbacks carry only indices, so album keys and the current item of an insert
 * come from an [InsertionSource] that the player fills in before changing the playlist.
 */
class AlbumShuffleOrder private constructor(
    private val keys: List<String?>,
    private val order: IntArray,
    private val random: Random,
    private val insertions: InsertionSource,
) : ShuffleOrder {
    /** What an insert needs to know that [ShuffleOrder]'s callbacks do not pass. */
    interface InsertionSource {
        /** Album keys of the [count] items being inserted, in queue order. Null or a wrong size means unknown. */
        fun albumKeys(count: Int): List<String?>?

        /** The current item's queue index before the insert, or [C.INDEX_UNSET]. */
        fun currentIndex(): Int
    }

    /** For orders that never see inserts with known albums: inserted tracks have no album. */
    object NoInsertions : InsertionSource {
        override fun albumKeys(count: Int): List<String?>? = null

        override fun currentIndex(): Int = C.INDEX_UNSET
    }

    private val positions = IntArray(order.size).also { positions ->
        order.forEachIndexed { position, index -> positions[index] = position }
    }

    /** Queue indices in the order they play. */
    fun playOrder(): List<Int> = order.toList()

    /** The album key of each queue index. */
    fun albumKeys(): List<String?> = keys

    fun withInsertions(insertions: InsertionSource): AlbumShuffleOrder =
        AlbumShuffleOrder(keys, order, random, insertions)

    /** The same queue played in [playOrder], for example after the user reorders the shuffled queue. */
    fun withPlayOrder(playOrder: List<Int>): AlbumShuffleOrder = fromPlayOrder(keys, playOrder, random, insertions)

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int = order.getOrElse(positions[index] + 1) { C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int = order.getOrElse(positions[index] - 1) { C.INDEX_UNSET }

    override fun getLastIndex(): Int = order.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = order.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): AlbumShuffleOrder {
        val newKeys = insertedKeys(insertionCount)
        return create(newKeys, startIndex, random, insertions)
    }

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): AlbumShuffleOrder {
        val inserted = insertedKeys(insertionCount)
        val newKeys = keys.subList(0, insertionIndex) + inserted + keys.subList(insertionIndex, keys.size)
        val current = insertions.currentIndex().let {
            when {
                it !in keys.indices -> C.INDEX_UNSET
                it >= insertionIndex -> it + insertionCount
                else -> it
            }
        }
        val playOrder = order.mapTo(ArrayList(newKeys.size)) { if (it >= insertionIndex) it + insertionCount else it }
        var remaining = (insertionIndex until insertionIndex + insertionCount).toList()

        // Join the previous track's album block, right after that track.
        val previous = insertionIndex - 1
        if (previous >= 0 && newKeys[previous] != null) {
            val joining = remaining.takeWhile { newKeys[it] == newKeys[previous] }
            playOrder.addAll(playOrder.indexOf(previous) + 1, joining)
            remaining = remaining.drop(joining.size)
        }
        // Join the next track's album block, right before that track.
        val next = insertionIndex + insertionCount
        if (next < newKeys.size && newKeys[next] != null) {
            val joining = remaining.takeLastWhile { newKeys[it] == newKeys[next] }
            playOrder.addAll(playOrder.indexOf(next), joining)
            remaining = remaining.dropLast(joining.size)
        }
        if (remaining.isNotEmpty()) {
            val position = when {
                insertionIndex == keys.size -> playOrder.size
                current != C.INDEX_UNSET -> blockEnd(playOrder, newKeys, playOrder.indexOf(current)) + 1
                previous >= 0 -> blockEnd(playOrder, newKeys, playOrder.indexOf(previous)) + 1
                else -> playOrder.size
            }
            playOrder.addAll(position, blocks(newKeys, remaining).flatten())
        }
        return AlbumShuffleOrder(newKeys, playOrder.toIntArray(), random, insertions)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): AlbumShuffleOrder {
        val count = indexToExclusive - indexFrom
        val playOrder = order.filter { it !in indexFrom until indexToExclusive }
            .map { if (it >= indexToExclusive) it - count else it }
        val newKeys = keys.subList(0, indexFrom) + keys.subList(indexToExclusive, keys.size)
        return AlbumShuffleOrder(newKeys, playOrder.toIntArray(), random, insertions)
    }

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): AlbumShuffleOrder {
        // oldIndexAt[newIndex] = the old queue index now at newIndex.
        val oldIndexAt = keys.indices.toMutableList()
        val moved = oldIndexAt.subList(indexFrom, indexToExclusive).toList()
        oldIndexAt.subList(indexFrom, indexToExclusive).clear()
        oldIndexAt.addAll(newIndexFrom, moved)
        val newIndexOf = IntArray(keys.size).also { map -> oldIndexAt.forEachIndexed { new, old -> map[old] = new } }
        val playOrder = IntArray(order.size) { newIndexOf[order[it]] }
        return AlbumShuffleOrder(oldIndexAt.map { keys[it] }, playOrder, random, insertions)
    }

    override fun cloneAndClear(): AlbumShuffleOrder = AlbumShuffleOrder(emptyList(), IntArray(0), random, insertions)

    private fun insertedKeys(count: Int): List<String?> =
        insertions.albumKeys(count)?.takeIf { it.size == count } ?: List(count) { null }

    companion object {
        /**
         * A fresh album shuffle of a queue with these album [keys]. [currentIndex]'s block plays
         * first; pass [C.INDEX_UNSET] when nothing is current.
         */
        fun create(
            keys: List<String?>,
            currentIndex: Int,
            random: Random,
            insertions: InsertionSource = NoInsertions,
        ): AlbumShuffleOrder {
            val blocks = blocks(keys, keys.indices.toList()).toMutableList()
            for (i in blocks.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                blocks[i] = blocks[j].also { blocks[j] = blocks[i] }
            }
            val currentBlock = blocks.indexOfFirst { currentIndex in it }
            if (currentBlock > 0) blocks.add(0, blocks.removeAt(currentBlock))
            return AlbumShuffleOrder(keys.toList(), blocks.flatten().toIntArray(), random, insertions)
        }

        /** An order that plays exactly [playOrder], for restoring a saved order or a user's reordering. */
        fun fromPlayOrder(
            keys: List<String?>,
            playOrder: List<Int>,
            random: Random = Random.Default,
            insertions: InsertionSource = NoInsertions,
        ): AlbumShuffleOrder {
            require(playOrder.sorted() == keys.indices.toList()) { "playOrder must be a permutation of the queue" }
            return AlbumShuffleOrder(keys.toList(), playOrder.toIntArray(), random, insertions)
        }

        /** Groups [indices] (in queue order) into album blocks; see the class rules. */
        private fun blocks(keys: List<String?>, indices: List<Int>): List<List<Int>> {
            val blocks = mutableListOf<MutableList<Int>>()
            val byAlbum = HashMap<String, MutableList<Int>>()
            for (index in indices) {
                val key = keys[index]
                if (key == null) {
                    blocks += mutableListOf(index)
                } else {
                    byAlbum.getOrPut(key) { mutableListOf<Int>().also { blocks += it } } += index
                }
            }
            return blocks
        }

        /** The last play position of the album block that contains [position]. */
        private fun blockEnd(playOrder: List<Int>, keys: List<String?>, position: Int): Int {
            val key = keys[playOrder[position]] ?: return position
            var end = position
            while (end + 1 < playOrder.size && keys[playOrder[end + 1]] == key) end++
            return end
        }
    }
}
