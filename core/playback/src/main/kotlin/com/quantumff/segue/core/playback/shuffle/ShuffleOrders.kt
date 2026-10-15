package com.quantumff.segue.core.playback.shuffle

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.quantumff.segue.core.model.ShuffleMode
import kotlin.random.Random

/**
 * Helpers for the three shuffle modes. Off plays the queue in order whatever the shuffle order is,
 * so switching modes never touches the queue and Off restores it losslessly. Tracks uses Media3's
 * [DefaultShuffleOrder]; Albums uses [AlbumShuffleOrder].
 */
object ShuffleOrders {
    /** A track shuffle of [length] items that starts with [currentIndex] (when it is a valid index). */
    fun tracks(length: Int, currentIndex: Int, random: Random): ShuffleOrder {
        val order = (0 until length).toMutableList()
        for (i in order.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            order[i] = order[j].also { order[j] = order[i] }
        }
        if (currentIndex in 0 until length) {
            order.remove(currentIndex)
            order.add(0, currentIndex)
        }
        return DefaultShuffleOrder(order.toIntArray(), random.nextLong())
    }

    /** Queue indices in the order [order] plays them. */
    fun playOrder(order: ShuffleOrder): List<Int> {
        if (order is AlbumShuffleOrder) return order.playOrder()
        val result = ArrayList<Int>(order.length)
        var index = order.firstIndex
        while (index != C.INDEX_UNSET) {
            result += index
            index = order.getNextIndex(index)
        }
        return result
    }

    /** What actually plays: the queue order when shuffle is off, otherwise [order]. */
    fun effectivePlayOrder(mode: ShuffleMode, order: ShuffleOrder): List<Int> =
        if (mode == ShuffleMode.Off) (0 until order.length).toList() else playOrder(order)

    /**
     * Moves the just-inserted queue items `insertedFrom until insertedFrom + insertedCount` to play
     * right after [currentIndex] (an index in the new queue). [DefaultShuffleOrder] scatters inserts
     * randomly, which would make "play next" in track shuffle play at a random time.
     */
    fun playInsertedNext(
        order: ShuffleOrder,
        insertedFrom: Int,
        insertedCount: Int,
        currentIndex: Int,
        random: Random = Random.Default,
    ): ShuffleOrder {
        val inserted = insertedFrom until insertedFrom + insertedCount
        val rest = playOrder(order).filterNot { it in inserted }.toMutableList()
        rest.addAll(rest.indexOf(currentIndex) + 1, inserted.toList())
        return withPlayOrder(order, rest, random)
    }

    /**
     * Moves the [count] items from play position [from] so the first of them ends up at play
     * position [to] (like [androidx.media3.common.Player.moveMediaItems]).
     */
    fun moveInPlayOrder(
        order: ShuffleOrder,
        from: Int,
        to: Int,
        count: Int = 1,
        random: Random = Random.Default,
    ): ShuffleOrder {
        val playOrder = playOrder(order).toMutableList()
        val block = playOrder.subList(from, from + count)
        val moved = block.toList()
        block.clear()
        playOrder.addAll(to, moved)
        return withPlayOrder(order, playOrder, random)
    }

    /** [order] changed to play exactly [playOrder], keeping its kind (album or track shuffle). */
    fun withPlayOrder(order: ShuffleOrder, playOrder: List<Int>, random: Random): ShuffleOrder =
        if (order is AlbumShuffleOrder) {
            order.withPlayOrder(playOrder)
        } else {
            DefaultShuffleOrder(playOrder.toIntArray(), random.nextLong())
        }
}
