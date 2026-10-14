package com.quantumff.segue.core.playback.shuffle

import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ShuffleMode
import kotlin.random.Random
import org.junit.Test

class ShuffleOrdersTest {
    private val keys = listOf("a", "a", "b", "b", "c", "d")

    @Test
    fun `track shuffle is a DefaultShuffleOrder that starts with the current track`() {
        repeat(50) { seed ->
            val order = ShuffleOrders.tracks(length = 10, currentIndex = 6, random = Random(seed))
            assertThat(order).isInstanceOf(DefaultShuffleOrder::class.java)
            val playOrder = ShuffleOrders.playOrder(order)
            assertThat(playOrder.first()).isEqualTo(6)
            assertThat(playOrder.sorted()).isEqualTo((0 until 10).toList())
        }
    }

    @Test
    fun `the effective order is the queue order when shuffle is off`() {
        val order = AlbumShuffleOrder.create(keys, currentIndex = 4, random = Random(3))
        assertThat(ShuffleOrders.effectivePlayOrder(ShuffleMode.Off, order)).isEqualTo((0 until 6).toList())
        assertThat(ShuffleOrders.effectivePlayOrder(ShuffleMode.Albums, order)).isEqualTo(order.playOrder())
    }

    @Test
    fun `turning album shuffle off and on again restores the queue order losslessly`() {
        val albums = AlbumShuffleOrder.create(keys, currentIndex = 0, random = Random(9))
        val off = ShuffleOrders.effectivePlayOrder(ShuffleMode.Off, albums)
        val tracks = ShuffleOrders.tracks(keys.size, 0, Random(1))
        val offAgain = ShuffleOrders.effectivePlayOrder(ShuffleMode.Off, tracks)
        assertThat(off).isEqualTo(keys.indices.toList())
        assertThat(offAgain).isEqualTo(keys.indices.toList())
    }

    @Test
    fun `play next in track shuffle puts the inserted tracks right after the current one`() {
        val before = ShuffleOrders.tracks(length = 5, currentIndex = 2, random = Random(4))
        // ExoPlayer inserts two items at index 3 (after current 2) with its own random positions.
        val inserted = before.cloneAndInsert(3, 2)
        val fixed = ShuffleOrders.playInsertedNext(inserted, insertedFrom = 3, insertedCount = 2, currentIndex = 2)
        val playOrder = ShuffleOrders.playOrder(fixed)
        val currentPosition = playOrder.indexOf(2)
        assertThat(playOrder.subList(currentPosition + 1, currentPosition + 3)).containsExactly(3, 4).inOrder()
        assertThat(playOrder.sorted()).isEqualTo((0 until 7).toList())
    }

    @Test
    fun `moving in album shuffle play order keeps it an album order`() {
        val order = AlbumShuffleOrder.fromPlayOrder(keys, listOf(4, 0, 1, 2, 3, 5))
        val moved = ShuffleOrders.moveInPlayOrder(order, from = 5, to = 1)
        assertThat(moved).isInstanceOf(AlbumShuffleOrder::class.java)
        assertThat(ShuffleOrders.playOrder(moved)).containsExactly(4, 5, 0, 1, 2, 3).inOrder()
        assertThat((moved as AlbumShuffleOrder).albumKeys()).isEqualTo(keys)
    }

    @Test
    fun `moving in track shuffle play order`() {
        val order = DefaultShuffleOrder(intArrayOf(3, 1, 0, 2), 0L)
        val moved = ShuffleOrders.moveInPlayOrder(order, from = 0, to = 3)
        assertThat(ShuffleOrders.playOrder(moved)).containsExactly(1, 0, 2, 3).inOrder()
    }
}
