package com.quantumff.segue.core.playback.shuffle

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class AlbumShuffleOrderTest {
    private fun order(
        keys: List<String?>,
        current: Int = C.INDEX_UNSET,
        seed: Int = 1,
        insert: FakeInsertions = FakeInsertions(),
    ) = AlbumShuffleOrder.create(keys, current, Random(seed), insert)

    // --- Invariants -------------------------------------------------------------------------

    @Test
    fun `play order is a valid permutation with consistent next and previous links`() {
        repeat(200) { seed ->
            val keys = randomKeys(Random(seed))
            val order = order(keys, current = Random(seed).nextInt(keys.size), seed = seed)
            assertValid(order, keys.size)
        }
    }

    @Test
    fun `every album is one contiguous block in track order`() {
        repeat(200) { seed ->
            val keys = randomKeys(Random(seed))
            val playOrder = order(keys, seed = seed).playOrder()
            assertAlbumsContiguousInTimelineOrder(keys, playOrder)
        }
    }

    @Test
    fun `the block of the current item comes first`() {
        repeat(200) { seed ->
            val keys = randomKeys(Random(seed))
            val current = Random(seed + 1).nextInt(keys.size)
            val playOrder = order(keys, current = current, seed = seed).playOrder()
            val firstBlock = playOrder.takeWhile { keys[it] == keys[playOrder.first()] }
            if (keys[current] == null) {
                assertThat(playOrder.first()).isEqualTo(current)
            } else {
                assertThat(firstBlock).contains(current)
            }
        }
    }

    @Test
    fun `the same seed gives the same order and different seeds shuffle differently`() {
        val keys = (0 until 10).map { "album$it" }
        assertThat(order(keys, seed = 7).playOrder()).isEqualTo(order(keys, seed = 7).playOrder())
        val distinct = (0 until 20).map { order(keys, seed = it).playOrder() }.toSet()
        assertThat(distinct.size).isGreaterThan(10)
    }

    // --- Shapes -----------------------------------------------------------------------------

    @Test
    fun `a single album plays in track order`() {
        val keys = List(8) { "a" }
        assertThat(order(keys, current = 3).playOrder()).isEqualTo((0 until 8).toList())
    }

    @Test
    fun `all different albums is a plain shuffle with the current track first`() {
        val keys = (0 until 12).map { "album$it" }
        val playOrder = order(keys, current = 5, seed = 3).playOrder()
        assertThat(playOrder.sorted()).isEqualTo((0 until 12).toList())
        assertThat(playOrder.first()).isEqualTo(5)
    }

    @Test
    fun `an album split across the queue is joined into one block in order of first appearance`() {
        // a a b a c: the second run of a joins the first.
        val keys = listOf("a", "a", "b", "a", "c")
        repeat(50) { seed ->
            val playOrder = order(keys, seed = seed).playOrder()
            val start = playOrder.indexOf(0)
            assertThat(playOrder.subList(start, start + 3)).containsExactly(0, 1, 3).inOrder()
        }
    }

    @Test
    fun `tracks without an album each form their own block`() {
        val keys = listOf(null, null, null, null, null, null)
        val orders = (0 until 20).map { order(keys, seed = it).playOrder() }.toSet()
        assertThat(orders.size).isGreaterThan(1)
        orders.forEach { assertThat(it.sorted()).isEqualTo((0 until 6).toList()) }
    }

    @Test
    fun `an empty queue has no first or last index`() {
        val order = order(emptyList())
        assertThat(order.length).isEqualTo(0)
        assertThat(order.firstIndex).isEqualTo(C.INDEX_UNSET)
        assertThat(order.lastIndex).isEqualTo(C.INDEX_UNSET)
    }

    // --- cloneAndSet (setMediaItems) --------------------------------------------------------

    @Test
    fun `setting a new playlist reads album keys of the new items and starts with the start item's block`() {
        val insert = FakeInsertions(keys = listOf("x", "x", "y", "z", "z", "z"))
        val order = order(emptyList(), insert = insert).cloneAndSet(6, 4)
        val playOrder = order.playOrder()
        assertThat(playOrder.take(3)).containsExactly(3, 4, 5).inOrder()
        assertValid(order, 6)
        assertAlbumsContiguousInTimelineOrder(insert.keys!!, playOrder)
    }

    // --- cloneAndInsert ---------------------------------------------------------------------

    @Test
    fun `inserted tracks of the previous track's album play right after it`() {
        // Album a = 0,1,2 ; b = 3,4. Insert one more "a" track at timeline index 2.
        val base = AlbumShuffleOrder.fromPlayOrder(listOf("a", "a", "a", "b", "b"), listOf(3, 4, 0, 1, 2))
        val insert = FakeInsertions(keys = listOf("a"), current = 3)
        val order = base.withInsertions(insert).cloneAndInsert(2, 1)
        assertThat(order.playOrder()).containsExactly(4, 5, 0, 1, 2, 3).inOrder()
        assertValid(order, 6)
    }

    @Test
    fun `inserted tracks of the next track's album play right before it`() {
        val base = AlbumShuffleOrder.fromPlayOrder(listOf("a", "a", "b", "b"), listOf(2, 3, 0, 1))
        val insert = FakeInsertions(keys = listOf("b"), current = 0)
        // Inserted at timeline 2, between a and b: matches its successor (b).
        val order = base.withInsertions(insert).cloneAndInsert(2, 1)
        assertThat(order.playOrder()).containsExactly(2, 3, 4, 0, 1).inOrder()
    }

    @Test
    fun `play next of another album forms a new block right after the current album`() {
        // Play order: b(3,4) a(0,1,2) c(5). Current is 0 (album a); insert "d" at 1.
        val base = AlbumShuffleOrder.fromPlayOrder(listOf("a", "a", "a", "b", "b", "c"), listOf(3, 4, 0, 1, 2, 5))
        val insert = FakeInsertions(keys = listOf("d", "d"), current = 0)
        val order = base.withInsertions(insert).cloneAndInsert(1, 2)
        // a is now 0,3,4; b 5,6; c 7; d 1,2.
        assertThat(order.playOrder()).containsExactly(5, 6, 0, 3, 4, 1, 2, 7).inOrder()
        assertValid(order, 8)
    }

    @Test
    fun `appending another album adds it at the end of the play order`() {
        val base = AlbumShuffleOrder.fromPlayOrder(listOf("a", "a", "b"), listOf(2, 0, 1))
        val insert = FakeInsertions(keys = listOf("c", "c", "d"), current = 2)
        val order = base.withInsertions(insert).cloneAndInsert(3, 3)
        assertThat(order.playOrder()).containsExactly(2, 0, 1, 3, 4, 5).inOrder()
    }

    @Test
    fun `inserting before every item without a current item still keeps the order valid`() {
        val base = AlbumShuffleOrder.fromPlayOrder(listOf("a", "b"), listOf(1, 0))
        val order = base.withInsertions(FakeInsertions(keys = listOf("c"))).cloneAndInsert(0, 1)
        assertValid(order, 3)
        assertThat(order.playOrder()).containsExactly(2, 1, 0).inOrder()
    }

    @Test
    fun `unknown album keys are treated as tracks without an album`() {
        val base = AlbumShuffleOrder.fromPlayOrder(listOf("a", "a"), listOf(0, 1))
        val order = base.withInsertions(FakeInsertions(keys = null, current = 0)).cloneAndInsert(1, 2)
        assertValid(order, 4)
        assertThat(order.albumKeys()).containsExactly("a", null, null, "a").inOrder()
    }

    // --- cloneAndRemove / cloneAndClear / cloneAndMove --------------------------------------

    @Test
    fun `removing tracks keeps the remaining play order and album blocks`() {
        val keys = listOf("a", "a", "b", "b", "c")
        val base = AlbumShuffleOrder.fromPlayOrder(keys, listOf(4, 2, 3, 0, 1))
        val order = base.cloneAndRemove(1, 3) // removes timeline 1 (a) and 2 (b)
        assertThat(order.playOrder()).containsExactly(2, 1, 0).inOrder()
        assertThat(order.albumKeys()).containsExactly("a", "b", "c").inOrder()
        assertValid(order, 3)
    }

    @Test
    fun `clearing empties the order`() {
        val order = order(listOf("a", "b")).cloneAndClear()
        assertThat(order.length).isEqualTo(0)
    }

    @Test
    fun `moving tracks in the timeline keeps when each track plays`() {
        val keys = listOf("a", "a", "b", "c")
        val base = AlbumShuffleOrder.fromPlayOrder(keys, listOf(3, 2, 0, 1))
        // Move timeline item 0 to index 2: new timeline is [old1, old2, old0, old3].
        val order = base.cloneAndMove(0, 1, 2)
        val oldToNew = mapOf(1 to 0, 2 to 1, 0 to 2, 3 to 3)
        assertThat(order.playOrder()).isEqualTo(listOf(3, 2, 0, 1).map { oldToNew.getValue(it) })
        assertThat(order.albumKeys()).containsExactly("a", "b", "a", "c").inOrder()
        assertValid(order, 4)
    }

    @Test
    fun `random edits keep the order a valid permutation`() {
        repeat(100) { seed ->
            val random = Random(seed)
            val insert = FakeInsertions()
            var keys = randomKeys(random).toMutableList()
            var order: ShuffleOrder = order(keys, current = 0, seed = seed, insert = insert)
            repeat(30) {
                when (random.nextInt(3)) {
                    0 -> {
                        val index = random.nextInt(keys.size + 1)
                        val added = List(random.nextInt(1, 4)) { "album${random.nextInt(5)}" }
                        insert.keys = added
                        insert.current = if (keys.isEmpty()) C.INDEX_UNSET else random.nextInt(keys.size)
                        order = order.cloneAndInsert(index, added.size)
                        keys.addAll(index, added)
                    }

                    1 -> if (keys.isNotEmpty()) {
                        val from = random.nextInt(keys.size)
                        val to = random.nextInt(from, keys.size) + 1
                        order = order.cloneAndRemove(from, to)
                        keys = (keys.subList(0, from) + keys.subList(to, keys.size)).toMutableList()
                    }

                    else -> if (keys.size > 1) {
                        val from = random.nextInt(keys.size - 1)
                        val to = from + 1
                        val newFrom = random.nextInt(keys.size)
                        order = order.cloneAndMove(from, to, newFrom)
                        val moved = keys.removeAt(from)
                        keys.add(newFrom, moved)
                    }
                }
                assertValid(order, keys.size)
                assertThat((order as AlbumShuffleOrder).albumKeys()).isEqualTo(keys)
            }
        }
    }

    // --- Helpers ----------------------------------------------------------------------------

    private class FakeInsertions(var keys: List<String?>? = emptyList(), var current: Int = C.INDEX_UNSET) :
        AlbumShuffleOrder.InsertionSource {
        override fun albumKeys(count: Int): List<String?>? = keys
        override fun currentIndex(): Int = current
    }

    private fun randomKeys(random: Random): List<String?> {
        val size = random.nextInt(1, 25)
        val keys = mutableListOf<String?>()
        while (keys.size < size) {
            val album = if (random.nextInt(8) == 0) null else "album${random.nextInt(8)}"
            repeat(random.nextInt(1, 5)) { if (keys.size < size) keys += album }
        }
        return keys
    }

    private fun ShuffleOrder.playOrder(): List<Int> = (this as AlbumShuffleOrder).playOrder()

    private fun assertValid(order: ShuffleOrder, length: Int) {
        assertThat(order.length).isEqualTo(length)
        val sequence = mutableListOf<Int>()
        var index = order.firstIndex
        while (index != C.INDEX_UNSET) {
            sequence += index
            index = order.getNextIndex(index)
        }
        assertThat(sequence.sorted()).isEqualTo((0 until length).toList())
        if (length > 0) {
            assertThat(order.lastIndex).isEqualTo(sequence.last())
            sequence.zipWithNext().forEach { (a, b) -> assertThat(order.getPreviousIndex(b)).isEqualTo(a) }
            assertThat(order.getPreviousIndex(sequence.first())).isEqualTo(C.INDEX_UNSET)
        }
    }

    private fun assertAlbumsContiguousInTimelineOrder(keys: List<String?>, playOrder: List<Int>) {
        keys.filterNotNull().distinct().forEach { album ->
            val positions = playOrder.indices.filter { keys[playOrder[it]] == album }
            assertThat(positions.last() - positions.first() + 1).isEqualTo(positions.size)
            val tracks = positions.map { playOrder[it] }
            assertThat(tracks).isInOrder()
        }
    }
}
